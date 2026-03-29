package backend.service.impl;

import backend.dto.cart.AddToCartRequest;
import backend.dto.cart.CartItemDto;
import backend.dto.cart.CartResponse;
import backend.dto.cart.UpdateCartQuantityRequest;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.CartItem;
import backend.model.entity.Product;
import backend.model.entity.User;
import backend.repository.CartItemRepository;
import backend.repository.ProductRepository;
import backend.repository.UserRepository;
import backend.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;

    // ── Mapping helper ──────────────────────────────────────────────────────────

    private CartItemDto toDto(CartItem ci) {
        Product p = ci.getProduct();
        long price = p.getPrice() != null ? p.getPrice().longValue() : 0L;
        long originalPrice = p.getOriginalPrice() != null ? p.getOriginalPrice().longValue() : 0L;
        BigDecimal lineTotal = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(ci.getQuantity()));
        return new CartItemDto(
                ci.getId(),
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getThumbnailUrl(),
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                price,
                originalPrice,
                ci.getQuantity(),
                p.getStock(),
                lineTotal
        );
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        List<CartItem> items = cartItemRepo.findByUserIdWithProduct(userId);
        List<CartItemDto> dtos = items.stream().map(this::toDto).toList();
        BigDecimal subtotal = dtos.stream()
                .map(CartItemDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(dtos, dtos.size(), subtotal);
    }

    @Override
    @Transactional
    public CartItemDto addItem(UUID userId, AddToCartRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() < 1) {
            throw new AuthException(CustomCode.BAD_REQUEST);
        }

        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> new AuthException(CustomCode.PRODUCT_NOT_FOUND));

        if (product.getStock() < 1) {
            throw new AuthException(CustomCode.INSUFFICIENT_STOCK);
        }

        CartItem item = cartItemRepo.findByUserIdAndProductId(userId, request.productId())
                .orElseGet(() -> {
                    User user = userRepo.findById(userId)
                            .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));
                    CartItem ci = new CartItem();
                    ci.setUser(user);
                    ci.setProduct(product);
                    ci.setQuantity(0);
                    return ci;
                });

        int newQty = item.getQuantity() + request.quantity();
        if (newQty > product.getStock()) {
            throw new AuthException(CustomCode.INSUFFICIENT_STOCK);
        }
        item.setQuantity(newQty);
        return toDto(cartItemRepo.save(item));
    }

    @Override
    @Transactional
    public CartItemDto updateQuantity(UUID userId, UUID productId, UpdateCartQuantityRequest request) {
        if (request.quantity() == null || request.quantity() < 1) {
            throw new AuthException(CustomCode.BAD_REQUEST);
        }

        CartItem item = cartItemRepo.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new AuthException(CustomCode.CART_ITEM_NOT_FOUND));

        if (request.quantity() > item.getProduct().getStock()) {
            throw new AuthException(CustomCode.INSUFFICIENT_STOCK);
        }

        item.setQuantity(request.quantity());
        return toDto(cartItemRepo.save(item));
    }

    @Override
    @Transactional
    public void removeItem(UUID userId, UUID productId) {
        if (cartItemRepo.findByUserIdAndProductId(userId, productId).isEmpty()) {
            throw new AuthException(CustomCode.CART_ITEM_NOT_FOUND);
        }
        cartItemRepo.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional
    public void clearCart(UUID userId) {
        cartItemRepo.deleteAllByUserId(userId);
    }
}
