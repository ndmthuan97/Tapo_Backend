package backend.dto.flashsale;

import backend.model.enums.FlashSaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for flash sale — java-pro: Java 21 record, immutable.
 */
public record FlashSaleDto(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        String thumbnailUrl,
        BigDecimal originalPrice,
        BigDecimal salePrice,
        int discountPercent,
        int stockLimit,
        int soldCount,
        int remaining,
        Instant startTime,
        Instant endTime,
        FlashSaleStatus status,
        long remainingSeconds
) {}
