package backend.dto.voucher;

import java.math.BigDecimal;

public record ValidateVoucherResponse(
        VoucherDto voucher,
        BigDecimal discountAmount
) {}
