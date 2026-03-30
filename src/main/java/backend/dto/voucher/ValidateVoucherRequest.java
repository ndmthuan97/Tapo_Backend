package backend.dto.voucher;

import java.math.BigDecimal;

public record ValidateVoucherRequest(
        String code,
        BigDecimal subtotal
) {}
