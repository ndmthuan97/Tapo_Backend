package backend.service;

import backend.dto.review.AdminReviewDto;
import backend.dto.review.BulkReviewActionRequest;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReviewService {

    Page<ReviewDto> getProductReviews(UUID productId, Pageable pageable);

    /** Returns {canReview: boolean, orderId: UUID|null} */
    Map<String, Object> canReview(UUID userId, UUID productId);

    ReviewDto createReview(UUID userId, CreateReviewRequest request);

    // ── Admin ─────────────────────────────────────────────────────────────────

    Page<AdminReviewDto> listAllReviews(ReviewStatus status, Integer rating, Pageable pageable);

    AdminReviewDto approveReview(UUID reviewId);

    AdminReviewDto rejectReview(UUID reviewId);

    long countPendingReviews();

    /** Admin gửi phản hồi cho một đánh giá. */
    AdminReviewDto replyReview(UUID reviewId, String reply);

    /**
     * Bulk approve hoặc reject nhiều reviews cùng lúc.
     * @return danh sách reviewId đã được xử lý thành công
     */
    List<UUID> bulkAction(List<UUID> reviewIds, BulkReviewActionRequest.BulkAction action);
}
