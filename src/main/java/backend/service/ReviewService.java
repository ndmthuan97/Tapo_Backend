package backend.service;

import backend.dto.review.CreateReviewRequest;
import backend.dto.review.ReviewDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    Page<ReviewDto> getProductReviews(UUID productId, Pageable pageable);

    boolean canReview(UUID userId, UUID productId);

    ReviewDto createReview(UUID userId, CreateReviewRequest request);
}
