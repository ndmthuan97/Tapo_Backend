package backend.dto.product;

import backend.model.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer stock,
        ProductStatus status,
        String thumbnailUrl,
        BigDecimal avgRating,
        Integer reviewCount,
        Integer soldCount,
        UUID categoryId,
        String categoryName,
        UUID brandId,
        String brandName,
        Map<String, String> specifications,
        Instant createdAt,
        Instant updatedAt
) {}
