package backend.dto.voucher;

import backend.model.enums.DiscountType;
import backend.model.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VoucherDto(
        UUID id,
        String code,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minimumOrderValue,
        Integer usageLimit,
        Integer usageCount,
        Instant startDate,
        Instant endDate,
        VoucherStatus status
) {}
