package backend.dto.inventory;

import backend.model.enums.ReceiptType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request to create a new inventory receipt (manual IMPORT or EXPORT adjustment).
 */
public record CreateReceiptRequest(
        @NotNull(message = "Loại phiếu là bắt buộc")
        ReceiptType type,

        String note,

        @NotEmpty(message = "Phiếu phải có ít nhất 1 sản phẩm")
        @Valid
        List<ItemEntry> items
) {
    public record ItemEntry(
            @NotNull UUID productId,
            @NotNull @Min(1) Integer quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice
    ) {}
}
