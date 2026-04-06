package backend.controller;

import backend.dto.cart.AddToCartRequest;
import backend.dto.cart.CartItemDto;
import backend.dto.cart.CartResponse;
import backend.dto.cart.UpdateCartQuantityRequest;
import backend.dto.common.ApiResponse;
import backend.security.CustomUserDetails;
import backend.service.CartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Quản lý giỏ hàng")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(principal.getId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemDto>> addItem(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody AddToCartRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(cartService.addItem(principal.getId(), request)));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartItemDto>> updateQuantity(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID productId,
            @RequestBody UpdateCartQuantityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                cartService.updateQuantity(principal.getId(), productId, request)
        ));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID productId
    ) {
        cartService.removeItem(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sản phẩm khỏi giỏ hàng", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        cartService.clearCart(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xóa toàn bộ giỏ hàng", null));
    }
}
