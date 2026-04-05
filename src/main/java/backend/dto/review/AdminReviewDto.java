package backend.dto.review;

import backend.model.enums.ReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Extended ReviewDto for Admin panel — includes product info and review status.
 */
public record AdminReviewDto(
        UUID id,
        // Reviewer
        UUID userId,
        String userName,
        String userAvatar,
        // Product
        UUID productId,
        String productName,
        String productThumbnail,
        // Review content
        Integer rating,
        String comment,
        List<String> images,
        ReviewStatus status,
        Instant createdAt
) {}
