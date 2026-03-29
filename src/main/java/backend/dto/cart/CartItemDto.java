package backend.dto.cart;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemDto(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        String thumbnailUrl,
        String brandName,
        String categoryName,
        Long price,
        Long originalPrice,
        Integer quantity,
        Integer stock,
        BigDecimal lineTotal
) {}
