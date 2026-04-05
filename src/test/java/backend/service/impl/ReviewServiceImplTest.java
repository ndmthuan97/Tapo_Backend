package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.review.CreateReviewRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl — Unit Tests")
class ReviewServiceImplTest {

    @Mock ReviewRepository reviewRepo;
    @Mock ProductRepository productRepo;
    @Mock UserRepository   userRepo;
    @Mock OrderRepository  orderRepo;

    @InjectMocks ReviewServiceImpl reviewService;

    // ── canReview ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("canReview: has purchased + not yet reviewed → true + orderId")
    void canReview_hasPurchasedAndNotReviewed_returnsTrueWithOrderId() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId   = UUID.randomUUID();

        given(reviewRepo.hasUserPurchasedProduct(userId, productId)).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(userId, productId)).willReturn(false);
        given(orderRepo.findDeliveredOrderIdByUserAndProduct(userId, productId))
                .willReturn(Optional.of(orderId));

        Map<String, Object> result = reviewService.canReview(userId, productId);

        assertThat(result.get("canReview")).isEqualTo(true);
        assertThat(result.get("orderId")).isEqualTo(orderId.toString());
    }

    @Test
    @DisplayName("canReview: already reviewed → false, orderId empty")
    void canReview_alreadyReviewed_returnsFalse() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        given(reviewRepo.hasUserPurchasedProduct(userId, productId)).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(userId, productId)).willReturn(true);

        Map<String, Object> result = reviewService.canReview(userId, productId);

        assertThat(result.get("canReview")).isEqualTo(false);
        assertThat(result.get("orderId")).isEqualTo("");
        then(orderRepo).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("canReview: product not purchased → false, orderId empty")
    void canReview_notPurchased_returnsFalse() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        given(reviewRepo.hasUserPurchasedProduct(userId, productId)).willReturn(false);
        given(reviewRepo.existsByUserIdAndProductId(userId, productId)).willReturn(false);

        Map<String, Object> result = reviewService.canReview(userId, productId);

        assertThat(result.get("canReview")).isEqualTo(false);
        then(orderRepo).shouldHaveNoInteractions();
    }

    // ── createReview ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createReview: not eligible → throws REVIEW_NOT_ELIGIBLE")
    void createReview_notEligible_throwsAuthException() {
        UUID userId  = UUID.randomUUID();
        UUID prodId  = UUID.randomUUID();
        var request  = new CreateReviewRequest(prodId, UUID.randomUUID(), 5, "Great!", List.of());

        given(reviewRepo.hasUserPurchasedProduct(userId, prodId)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining(CustomCode.REVIEW_NOT_ELIGIBLE.getDefaultMessage());

        then(reviewRepo).should(never()).save(any());
    }

    @Test
    @DisplayName("createReview: already reviewed → throws REVIEW_ALREADY_EXISTS")
    void createReview_alreadyReviewed_throwsAuthException() {
        UUID userId  = UUID.randomUUID();
        UUID prodId  = UUID.randomUUID();
        var request  = new CreateReviewRequest(prodId, UUID.randomUUID(), 5, "Great!", List.of());

        given(reviewRepo.hasUserPurchasedProduct(userId, prodId)).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(userId, prodId)).willReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining(CustomCode.REVIEW_ALREADY_EXISTS.getDefaultMessage());

        then(reviewRepo).should(never()).save(any());
    }

    @Test
    @DisplayName("createReview: eligible → saves review with PENDING status")
    void createReview_eligible_savesReviewAsPending() {
        UUID userId  = UUID.randomUUID();
        UUID prodId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var request  = new CreateReviewRequest(prodId, orderId, 4, "Good laptop!", List.of());

        User    user    = stubUser(userId);
        Product product = stubProduct(prodId);
        Order   order   = stubOrder(orderId);
        Review  saved   = stubReview(user, product, ReviewStatus.PENDING);

        given(reviewRepo.hasUserPurchasedProduct(userId, prodId)).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(userId, prodId)).willReturn(false);
        given(userRepo.findById(userId)).willReturn(Optional.of(user));
        given(productRepo.findById(prodId)).willReturn(Optional.of(product));
        given(orderRepo.findById(orderId)).willReturn(Optional.of(order));
        given(reviewRepo.save(any())).willReturn(saved);

        var dto = reviewService.createReview(userId, request);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        then(reviewRepo).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(dto).isNotNull();
    }

    // ── approveReview ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("approveReview: sets status APPROVED and recalculates product stats")
    void approveReview_setsApprovedStatusAndRecalculatesStats() {
        UUID reviewId = UUID.randomUUID();
        UUID prodId   = UUID.randomUUID();

        User    user    = stubUser(UUID.randomUUID());
        Product product = stubProduct(prodId);
        Review  review  = stubReview(user, product, ReviewStatus.PENDING);

        given(reviewRepo.findByIdForAdmin(reviewId)).willReturn(Optional.of(review));
        given(reviewRepo.save(any())).willReturn(review);
        given(reviewRepo.calculateAvgRating(prodId)).willReturn(4.5);
        given(reviewRepo.countApprovedReviews(prodId)).willReturn(10);

        reviewService.approveReview(reviewId);

        // Status set to APPROVED
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        // Stat recalculation called
        then(reviewRepo).should().calculateAvgRating(prodId);
        then(reviewRepo).should().countApprovedReviews(prodId);
        then(reviewRepo).should().updateProductRatingStats(eq(prodId), anyDouble(), eq(10));
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private User stubUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Test User");
        u.setAvatarUrl(null);
        return u;
    }

    private Product stubProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Product");
        return p;
    }

    private Order stubOrder(UUID id) {
        Order o = new Order();
        o.setId(id);
        return o;
    }

    private Review stubReview(User user, Product product, ReviewStatus status) {
        Review r = new Review();
        r.setId(UUID.randomUUID());
        r.setUser(user);
        r.setProduct(product);
        r.setRating(4);
        r.setComment("Good product");
        r.setStatus(status);
        return r;
    }
}
