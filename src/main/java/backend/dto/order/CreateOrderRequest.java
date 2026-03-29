package backend.dto.order;

import java.util.UUID;

public record CreateOrderRequest(
        UUID addressId,
        String customerNote,
        String voucherCode
) {}
