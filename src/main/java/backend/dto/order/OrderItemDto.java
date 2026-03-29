package backend.dto.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        String productName,
        String productThumbnail,
        Long unitPrice,
        Integer quantity,
        BigDecimal totalPrice
) {}
