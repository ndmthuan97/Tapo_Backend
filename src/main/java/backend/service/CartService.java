package backend.service;

import backend.dto.cart.AddToCartRequest;
import backend.dto.cart.CartItemDto;
import backend.dto.cart.CartResponse;
import backend.dto.cart.UpdateCartQuantityRequest;

import java.util.UUID;

public interface CartService {

    CartResponse getCart(UUID userId);

    CartItemDto addItem(UUID userId, AddToCartRequest request);

    CartItemDto updateQuantity(UUID userId, UUID productId, UpdateCartQuantityRequest request);

    void removeItem(UUID userId, UUID productId);

    void clearCart(UUID userId);
}
