package backend.dto.inventory;

import backend.model.enums.ReceiptType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for an inventory receipt (IMPORT or EXPORT).
 * java-pro: Java 21 record — immutable read model.
 */
public record InventoryReceiptDto(
        UUID id,
        String receiptCode,
        ReceiptType type,
        String createdByName,
        String orderCode,
        String note,
        List<ItemDto> items,
        Instant createdAt
) {
    /** Flattened line item view inside each receipt. */
    public record ItemDto(
            UUID productId,
            String productName,
            String thumbnailUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
