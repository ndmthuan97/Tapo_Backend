package backend.dto.flashsale;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for creating or updating a Flash Sale.
 */
public record FlashSaleRequest(
        @NotNull(message = "productId là bắt buộc")
        UUID productId,

        @NotNull(message = "Giá sale là bắt buộc")
        @DecimalMin(value = "0.01", message = "Giá sale phải lớn hơn 0")
        BigDecimal salePrice,

        @NotNull(message = "Giới hạn tồn kho là bắt buộc")
        @Min(value = 1, message = "Giới hạn tồn kho tối thiểu 1")
        Integer stockLimit,

        @NotNull(message = "Thời gian bắt đầu là bắt buộc")
        Instant startTime,

        @NotNull(message = "Thời gian kết thúc là bắt buộc")
        Instant endTime
) {}
