package backend.dto.voucher;

import backend.model.enums.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateVoucherRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull DiscountType discountType,
        @NotNull @Positive BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        @NotNull @PositiveOrZero BigDecimal minimumOrderValue,
        @Positive Integer usageLimit,
        @NotNull Instant startDate,
        @NotNull Instant endDate
) {}
