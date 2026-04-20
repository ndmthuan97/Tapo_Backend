package backend.service;

import backend.dto.common.CustomCode;
import backend.dto.review.AdminReviewDto;
import backend.dto.review.BulkReviewActionRequest;
import backend.dto.review.BulkReviewActionRequest.BulkAction;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.model.enums.ReviewStatus;
import backend.repository.*;
import backend.service.impl.ReviewServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService — Unit Tests")
class ReviewServiceImplTest {

    @Mock ReviewRepository reviewRepo;
    @Mock ProductRepository productRepo;
    @Mock UserRepository userRepo;
    @Mock OrderRepository orderRepo;

    @InjectMocks
    ReviewServiceImpl reviewService;

    // ══════════════════════════════════════════════════════════════════════════════
    // CREATE REVIEW
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createReview()")
    class CreateReviewTests {

        @Test
        @DisplayName("eligible user → review saved with PENDING status")
        void createReview_success() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.DELIVERED);

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
            assertThat(result.rating()).isEqualTo(5);
        }

        @Test
        @DisplayName("user has not purchased product → throw REVIEW_NOT_ELIGIBLE")
        void createReview_notEligible() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);

            given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(false);

            CreateReviewRequest req = TestDataFactory.createReviewRequest(product.getId(), UUID.randomUUID());

            assertThatThrownBy(() -> reviewService.createReview(user.getId(), req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.REVIEW_NOT_ELIGIBLE));
        }

        @Test
        @DisplayName("user already reviewed this product → throw REVIEW_ALREADY_EXISTS")
        void createReview_alreadyReviewed() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);

            given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
            given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(true);

            CreateReviewRequest req = TestDataFactory.createReviewRequest(product.getId(), UUID.randomUUID());

            assertThatThrownBy(() -> reviewService.createReview(user.getId(), req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.REVIEW_ALREADY_EXISTS));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CAN REVIEW
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("canReview()")
    class CanReviewTests {

        @Test
        @DisplayName("purchased and not yet reviewed → canReview=true with orderId")
        void canReview_true() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            UUID orderId = UUID.randomUUID();

            given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(true);
            given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(false);
            given(orderRepo.findDeliveredOrderIdByUserAndProduct(user.getId(), product.getId()))
                    .willReturn(Optional.of(orderId));

            Map<String, Object> result = reviewService.canReview(user.getId(), product.getId());

            assertThat(result.get("canReview")).isEqualTo(true);
            assertThat(result.get("orderId")).isEqualTo(orderId.toString());
        }

        @Test
        @DisplayName("not purchased → canReview=false, orderId empty")
        void canReview_notPurchased() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);

            given(reviewRepo.hasUserPurchasedProduct(user.getId(), product.getId())).willReturn(false);
            given(reviewRepo.existsByUserIdAndProductId(user.getId(), product.getId())).willReturn(false);

            Map<String, Object> result = reviewService.canReview(user.getId(), product.getId());

            assertThat(result.get("canReview")).isEqualTo(false);
            assertThat(result.get("orderId")).isEqualTo("");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // ADMIN OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("approveReview() & rejectReview()")
    class AdminTests {

        @Test
        @DisplayName("approveReview → status transitions to APPROVED, stats recalculated")
        void approveReview_success() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);
            Review review = TestDataFactory.pendingReview(user, product, order);

            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.save(any())).willReturn(review);
            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.calculateAvgRating(product.getId())).willReturn(4.5);
            given(reviewRepo.countApprovedReviews(product.getId())).willReturn(10);

            reviewService.approveReview(review.getId());

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
            verify(reviewRepo).updateProductRatingStats(eq(product.getId()), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("rejectReview → status transitions to REJECTED, stats recalculated")
        void rejectReview_success() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);
            Review review = TestDataFactory.pendingReview(user, product, order);

            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.save(any())).willReturn(review);
            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.calculateAvgRating(product.getId())).willReturn(3.0);
            given(reviewRepo.countApprovedReviews(product.getId())).willReturn(5);

            reviewService.rejectReview(review.getId());

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        }

        @Test
        @DisplayName("replyReview with valid content → reply and repliedAt saved")
        void replyReview_validContent() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);
            Review review = TestDataFactory.pendingReview(user, product, order);

            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.save(any())).willReturn(review);

            reviewService.replyReview(review.getId(), "Thank you for your review!");

            assertThat(review.getAdminReply()).isEqualTo("Thank you for your review!");
            assertThat(review.getRepliedAt()).isNotNull();
        }

        @Test
        @DisplayName("replyReview with null → clears existing reply")
        void replyReview_clearReply() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);
            Review review = TestDataFactory.pendingReview(user, product, order);
            review.setAdminReply("Old reply");

            given(reviewRepo.findByIdForAdmin(review.getId())).willReturn(Optional.of(review));
            given(reviewRepo.save(any())).willReturn(review);

            reviewService.replyReview(review.getId(), null);

            assertThat(review.getAdminReply()).isNull();
            assertThat(review.getRepliedAt()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BULK ACTION
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bulkAction()")
    class BulkActionTests {

        @Test
        @DisplayName("bulkAction APPROVE → all reviews transition to APPROVED")
        void bulkAction_approve() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);

            Review r1 = TestDataFactory.pendingReview(user, product, order);
            Review r2 = TestDataFactory.pendingReview(user, product, order);
            List<UUID> ids = List.of(r1.getId(), r2.getId());

            given(reviewRepo.findAllById(ids)).willReturn(List.of(r1, r2));
            given(reviewRepo.saveAll(any())).willReturn(List.of(r1, r2));
            given(reviewRepo.calculateAvgRating(product.getId())).willReturn(4.5);
            given(reviewRepo.countApprovedReviews(product.getId())).willReturn(2);

            List<UUID> processed = reviewService.bulkAction(ids, BulkAction.APPROVE);

            assertThat(r1.getStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(r2.getStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(processed).hasSize(2);
        }

        @Test
        @DisplayName("bulkAction REJECT → all reviews transition to REJECTED")
        void bulkAction_reject() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);

            Review r1 = TestDataFactory.pendingReview(user, product, order);
            List<UUID> ids = List.of(r1.getId());

            given(reviewRepo.findAllById(ids)).willReturn(List.of(r1));
            given(reviewRepo.saveAll(any())).willReturn(List.of(r1));
            given(reviewRepo.calculateAvgRating(product.getId())).willReturn(0.0);
            given(reviewRepo.countApprovedReviews(product.getId())).willReturn(0);

            reviewService.bulkAction(ids, BulkAction.REJECT);

            assertThat(r1.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        }

        @Test
        @DisplayName("recalculate avgRating rounds to 1 decimal place (4.666 → 4.7)")
        void recalculate_ratingRoundedToOneDecimal() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.pendingOrder(user);
            Review r = TestDataFactory.pendingReview(user, product, order);

            given(reviewRepo.findAllById(List.of(r.getId()))).willReturn(List.of(r));
            given(reviewRepo.saveAll(any())).willReturn(List.of(r));
            given(reviewRepo.calculateAvgRating(product.getId())).willReturn(4.666);
            given(reviewRepo.countApprovedReviews(product.getId())).willReturn(3);

            reviewService.bulkAction(List.of(r.getId()), BulkAction.APPROVE);

            // Verify updateProductRatingStats called with 4.7 (not 4.666)
            verify(reviewRepo).updateProductRatingStats(product.getId(), 4.7, 3);
        }
    }
}
