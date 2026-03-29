package backend.dto.cart;

import java.util.UUID;

public record AddToCartRequest(
        UUID productId,
        Integer quantity
) {}
