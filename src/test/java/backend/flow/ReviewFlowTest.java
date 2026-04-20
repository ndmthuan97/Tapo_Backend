package backend.flow;

import backend.dto.common.CustomCode;
import backend.dto.review.AdminReviewDto;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.exception.AuthException;
import backend.model.entity.Product;
import backend.model.entity.Review;
import backend.model.entity.User;
import backend.model.enums.OrderStatus;
import backend.model.enums.ReviewStatus;
import backend.repository.*;
import backend.service.impl.ReviewServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Flow Test: Review Eligibility and Approval Lifecycle.
 *
 * Flow: canReview() → createReview() → approveReview() / rejectReview()
 *
 * Business rules:
 * - Only users with a DELIVERED order for the product may submit a review.
 * - One review per user per product (product-level deduplication).
 * - Approval triggers avgRating + reviewCount recalculation via JPQL bulk update.
 * - Rejection also recalculates to restore accurate stats if previously approved.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Review Flow — Eligibility, Submit, Approve / Reject")
class ReviewFlowTest {

    @Mock ReviewRepository reviewRepo;
    @Mock OrderRepository orderRepo;
    @Mock ProductRepository productRepo;
    @Mock UserRepository userRepo;

    @InjectMocks
    ReviewServiceImpl reviewService;

    // ─── ELIGIBILITY CHECK ────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. User has a DELIVERED order → canReview = true, orderId populated")
    void canReview_withDeliveredOrder_returnsTrue() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);
        UUID expectedOrderId = UUID.randomUUID();

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(false);
        given(orderRepo.findDeliveredOrderIdByUserAndProduct(user.getId(), product.getId()))
                .willReturn(Optional.of(expectedOrderId));

        Map<String, Object> result = reviewService.canReview(user.getId(), product.getId());

        assertThat(result.get("canReview")).isEqualTo(true);
        assertThat(result.get("orderId")).isEqualTo(expectedOrderId.toString());
    }

    @Test
    @DisplayName("1b. User has not purchased product → canReview = false, orderId empty")
    void canReview_withoutPurchase_returnsFalse() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(5);

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(false);
        given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(false);

        Map<String, Object> result = reviewService.canReview(user.getId(), product.getId());

        assertThat(result.get("canReview")).isEqualTo(false);
        assertThat(result.get("orderId")).isEqualTo("");
        verify(orderRepo, never()).findDeliveredOrderIdByUserAndProduct(any(), any());
    }

    @Test
    @DisplayName("1c. User already reviewed the product → canReview = false")
    void canReview_alreadyReviewed_returnsFalse() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(true);

        Map<String, Object> result = reviewService.canReview(user.getId(), product.getId());

        assertThat(result.get("canReview")).isEqualTo(false);
    }

    // ─── SUBMIT REVIEW ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2. Submit valid review → saved with PENDING status, product rating unchanged")
    void createReview_validEligibility_createsPending() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.DELIVERED);

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(false);
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(productRepo.findById(product.getId())).willReturn(Optional.of(product));
        given(orderRepo.findById(order.getId())).willReturn(Optional.of(order));

        Review savedReview = TestDataFactory.pendingReview(user, product, order);
        given(reviewRepo.save(any(Review.class))).willReturn(savedReview);

        CreateReviewRequest req = TestDataFactory.createReviewRequest(product.getId(), order.getId());
        ReviewDto result = reviewService.createReview(user.getId(), req);

        assertThat(result).isNotNull();
        verify(reviewRepo).save(argThat(r -> r.getStatus() == ReviewStatus.PENDING));
        verifyNoMoreInteractions(productRepo); // rating not touched while PENDING
    }

    @Test
    @DisplayName("2b. Submit review without a purchased product → throw REVIEW_NOT_ELIGIBLE")
    void createReview_notPurchased_throwsNotEligible() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(false);

        CreateReviewRequest req = TestDataFactory.createReviewRequest(product.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> reviewService.createReview(user.getId(), req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.REVIEW_NOT_ELIGIBLE));
    }

    @Test
    @DisplayName("2c. Submit duplicate review for same product → throw REVIEW_ALREADY_EXISTS")
    void createReview_duplicate_throwsAlreadyExists() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);

        given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
        given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(true);

        CreateReviewRequest req = TestDataFactory.createReviewRequest(product.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> reviewService.createReview(user.getId(), req))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.REVIEW_ALREADY_EXISTS));
    }

    // ─── ADMIN APPROVE / REJECT ───────────────────────────────────────────────────

    @Test
    @DisplayName("3. Admin approves review → status APPROVED, updateProductRatingStats called correctly")
    void approveReview_updatesProductStats() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.DELIVERED);
        Review review = TestDataFactory.pendingReview(user, product, order);

        given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
        given(reviewRepo.save(any(Review.class))).willReturn(review);
        given(reviewRepo.calculateAvgRating(product.getId())).willReturn(4.5);
        given(reviewRepo.countApprovedReviews(product.getId())).willReturn(10);

        AdminReviewDto result = reviewService.approveReview(review.getId());

        assertThat(result.status()).isEqualTo(ReviewStatus.APPROVED);
        verify(reviewRepo).updateProductRatingStats(
                eq(product.getId()), doubleThat(d -> d == 4.5), eq(10));
    }

    @Test
    @DisplayName("3b. Admin rejects review → status REJECTED, stats recalculated to restore accuracy")
    void rejectReview_alsoRecalculatesStats() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(0);
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.DELIVERED);
        Review review = TestDataFactory.pendingReview(user, product, order);

        given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
        given(reviewRepo.save(any(Review.class))).willReturn(review);
        given(reviewRepo.calculateAvgRating(product.getId())).willReturn(0.0);
        given(reviewRepo.countApprovedReviews(product.getId())).willReturn(0);

        AdminReviewDto result = reviewService.rejectReview(review.getId());

        assertThat(result.status()).isEqualTo(ReviewStatus.REJECTED);
        verify(reviewRepo).updateProductRatingStats(eq(product.getId()), anyDouble(), eq(0));
    }
}
