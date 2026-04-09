package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.review.AdminReviewDto;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.dto.review.ReviewReplyRequest;
import backend.model.enums.ReviewStatus;
import backend.security.CustomUserDetails;
import backend.service.ReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Review", description = "Đánh giá sản phẩm")
public class ReviewController {

    private final ReviewService reviewService;

    // ── Public / Customer ─────────────────────────────────────────────────────

    /** GET /api/products/{productId}/reviews?page=0&size=10 */
    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<Page<ReviewDto>>> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getProductReviews(productId, pageable)
        ));
    }

    /** GET /api/reviews/can-review?productId=xxx */
    @GetMapping("/api/reviews/can-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> canReview(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.canReview(principal.getId(), productId)));
    }

    /** POST /api/reviews */
    @PostMapping("/api/reviews")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cảm ơn bạn đã đánh giá! Đánh giá sẽ được hiển thị sau khi được duyệt.",
                reviewService.createReview(principal.getId(), request)
        ));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/reviews?status=PENDING&rating=5&page=0&size=20
     * status: PENDING | APPROVED | REJECTED | (blank = all)
     * rating: 1-5 | (blank = all)
     */
    @GetMapping("/api/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdminReviewDto>>> listAllReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.listAllReviews(status, rating, pageable)
        ));
    }

    /** PATCH /api/admin/reviews/{id}/approve */
    @PatchMapping("/api/admin/reviews/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminReviewDto>> approveReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đánh giá đã được duyệt.",
                reviewService.approveReview(id)
        ));
    }

    /** PATCH /api/admin/reviews/{id}/reject */
    @PatchMapping("/api/admin/reviews/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminReviewDto>> rejectReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đánh giá đã bị từ chối.",
                reviewService.rejectReview(id)
        ));
    }

    /**
     * POST /api/admin/reviews/{id}/reply
     * Body: { "reply": "Cảm ơn bạn đã đánh giá..." }
     * Gửi reply='' hoặc reply=null để xóa phản hồi.
     */
    @PostMapping("/api/admin/reviews/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminReviewDto>> replyReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewReplyRequest body
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi phản hồi.",
                reviewService.replyReview(id, body.reply())
        ));
    }

    /** GET /api/admin/reviews/pending-count */
    @GetMapping("/api/admin/reviews/pending-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", reviewService.countPendingReviews())
        ));
    }
}
