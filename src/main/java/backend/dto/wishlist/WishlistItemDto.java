package backend.dto.wishlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WishlistItemDto(
        UUID wishlistId,
        UUID productId,
        String productName,
        String productSlug,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer stock,
        BigDecimal avgRating,
        Integer reviewCount,
        String brandName,
        String categoryName,
        Instant addedAt
) {}
