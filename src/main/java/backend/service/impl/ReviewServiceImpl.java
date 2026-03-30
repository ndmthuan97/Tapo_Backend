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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                r.getCreatedAt()
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
    public boolean canReview(UUID userId, UUID productId) {
        boolean hasPurchased = reviewRepo.hasUserPurchasedProduct(userId, productId);
        boolean alreadyReviewed = reviewRepo.existsByUserIdAndProductId(userId, productId);
        return hasPurchased && !alreadyReviewed;
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
    public AdminReviewDto approveReview(UUID reviewId) {
        Review review = reviewRepo.findByIdForAdmin(reviewId)
                .orElseThrow(() -> new AuthException(CustomCode.REVIEW_NOT_FOUND));
        review.setStatus(ReviewStatus.APPROVED);
        return toAdminDto(reviewRepo.save(review));
    }

    @Override
    @Transactional
    public AdminReviewDto rejectReview(UUID reviewId) {
        Review review = reviewRepo.findByIdForAdmin(reviewId)
                .orElseThrow(() -> new AuthException(CustomCode.REVIEW_NOT_FOUND));
        review.setStatus(ReviewStatus.REJECTED);
        return toAdminDto(reviewRepo.save(review));
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingReviews() {
        return reviewRepo.countByStatus(ReviewStatus.PENDING);
    }
}
