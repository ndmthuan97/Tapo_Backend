package backend.dto.order;

import java.util.UUID;

public record CreateOrderRequest(
        UUID addressId,
        String customerNote,
        String voucherCode,
        /** Phương thức thanh toán: COD, VNPAY, MOMO, BANK */
        String paymentMethod
) {}

