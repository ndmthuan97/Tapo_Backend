package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.review.AdminReviewDto;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.exception.AuthException;
import backend.model.entity.Order;
import backend.model.entity.Product;
import backend.model.entity.Review;
import backend.model.entity.User;
import backend.model.enums.ReviewStatus;
import backend.repository.OrderRepository;
import backend.repository.ProductRepository;
import backend.repository.ReviewRepository;
import backend.repository.UserRepository;
import backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;

    // ── Mapping ─────────────────────────────────────────────────────────────────

    private ReviewDto toDto(Review r) {
        User u = r.getUser();
        return new ReviewDto(
                r.getId(),
                u.getId(),
                u.getFullName(),
                u.getAvatarUrl(),
                r.getRating(),
                r.getComment(),
                r.getImages(),
                r.getCreatedAt()
        );
    }

    private AdminReviewDto toAdminDto(Review r) {
        User u = r.getUser();
        Product p = r.getProduct();
        return new AdminReviewDto(
                r.getId(),
                u.getId(),
                u.getFullName(),
                u.getAvatarUrl(),
                p.getId(),
                p.getName(),
                p.getThumbnailUrl(),
                r.getRating(),
                r.getComment(),
                r.getImages(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getAdminReply(),
                r.getRepliedAt()
        );
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getProductReviews(UUID productId, Pageable pageable) {
        return reviewRepo.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> canReview(UUID userId, UUID productId) {
        boolean hasPurchased   = reviewRepo.hasUserPurchasedProduct(userId, productId);
        boolean alreadyReviewed = reviewRepo.existsByUserIdAndProductId(userId, productId);
        boolean can = hasPurchased && !alreadyReviewed;
        UUID orderId = can
                ? orderRepo.findDeliveredOrderIdByUserAndProduct(userId, productId).orElse(null)
                : null;
        return Map.of(
                "canReview", can,
                "orderId",   orderId != null ? orderId.toString() : ""
        );
    }

    @Override
    @Transactional
    public ReviewDto createReview(UUID userId, CreateReviewRequest request) {
        // 1. Validate eligibility
        if (!reviewRepo.hasUserPurchasedProduct(userId, request.productId())) {
            throw new AuthException(CustomCode.REVIEW_NOT_ELIGIBLE);
        }

        // 2. Prevent duplicate review for same product+order
        if (reviewRepo.existsByUserIdAndProductId(userId, request.productId())) {
            throw new AuthException(CustomCode.REVIEW_ALREADY_EXISTS);
        }

        // 3. Load references
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));
        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> new AuthException(CustomCode.PRODUCT_NOT_FOUND));
        Order order = orderRepo.findById(request.orderId())
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));

        // 4. Persist review (starts in PENDING, admin approves later)
        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setOrder(order);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setImages(request.images());
        review.setStatus(ReviewStatus.PENDING);

        return toDto(reviewRepo.save(review));
    }

    // ── Admin operations ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReviewDto> listAllReviews(ReviewStatus status, Pageable pageable) {
        return reviewRepo.findAllForAdmin(status, pageable).map(this::toAdminDto);
    }

    @Override
    @Transactional
    // java-pro: allEntries=true -- we don't know productId before loading the review,
    // and admin-triggered approvals are low-frequency, so full eviction is acceptable
    @CacheEvict(value = "product-detail", allEntries = true)
    public AdminReviewDto approveReview(UUID reviewId) {
        Review review = reviewRepo.findByIdForAdmin(reviewId)
                .orElseThrow(() -> new AuthException(CustomCode.REVIEW_NOT_FOUND));
        review.setStatus(ReviewStatus.APPROVED);
        reviewRepo.save(review);
        recalculateProductStats(review.getProduct().getId());
        return toAdminDto(reviewRepo.findByIdForAdmin(reviewId).orElseThrow());
    }

    @Override
    @Transactional
    @CacheEvict(value = "product-detail", allEntries = true)
    public AdminReviewDto rejectReview(UUID reviewId) {
        Review review = reviewRepo.findByIdForAdmin(reviewId)
                .orElseThrow(() -> new AuthException(CustomCode.REVIEW_NOT_FOUND));
        review.setStatus(ReviewStatus.REJECTED);
        reviewRepo.save(review);
        recalculateProductStats(review.getProduct().getId());
        return toAdminDto(reviewRepo.findByIdForAdmin(reviewId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingReviews() {
        return reviewRepo.countByStatus(ReviewStatus.PENDING);
    }

    @Override
    @Transactional
    public AdminReviewDto replyReview(UUID reviewId, String reply) {
        Review review = reviewRepo.findByIdForAdmin(reviewId)
                .orElseThrow(() -> new AuthException(CustomCode.REVIEW_NOT_FOUND));
        review.setAdminReply(reply != null && !reply.isBlank() ? reply.strip() : null);
        review.setRepliedAt(reply != null && !reply.isBlank() ? java.time.Instant.now() : null);
        return toAdminDto(reviewRepo.save(review));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Recalculate avgRating and reviewCount on Product from all APPROVED reviews.
     * java-pro: single JPQL UPDATE avoids extra SELECT + dirty-check overhead.
     */
    private void recalculateProductStats(UUID productId) {
        double avg = reviewRepo.calculateAvgRating(productId);
        int count  = reviewRepo.countApprovedReviews(productId);
        // Round to 1 decimal (e.g. 4.67 → 4.7) for display consistency
        double rounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
        reviewRepo.updateProductRatingStats(productId, rounded, count);
    }
}
