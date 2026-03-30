package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import backend.security.CustomUserDetails;
import backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

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

    /** GET /api/reviews/can-review?productId=xxx — check if current user can review */
    @GetMapping("/api/reviews/can-review")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> canReview(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID productId
    ) {
        boolean can = reviewService.canReview(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("canReview", can)));
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
}
