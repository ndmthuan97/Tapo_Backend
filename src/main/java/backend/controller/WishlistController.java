package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.wishlist.WishlistItemDto;
import backend.security.CustomUserDetails;
import backend.service.WishlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Danh sách sản phẩm yêu thích")
public class WishlistController {

    private final WishlistService wishlistService;

    /** GET /api/wishlist?page=0&size=20 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WishlistItemDto>>> getWishlist(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                wishlistService.getWishlist(principal.getId(), pageable)
        ));
    }

    /** POST /api/wishlist/{productId} */
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistItemDto>> addToWishlist(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã thêm vào danh sách yêu thích",
                wishlistService.addToWishlist(principal.getId(), productId)
        ));
    }

    /** DELETE /api/wishlist/{productId} */
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID productId
    ) {
        wishlistService.removeFromWishlist(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa khỏi danh sách yêu thích", null));
    }

    /** GET /api/wishlist/check/{productId} */
    @GetMapping("/check/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkWishlisted(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID productId
    ) {
        boolean wishlisted = wishlistService.isWishlisted(principal.getId(), productId);
        long total = wishlistService.getWishlistCount(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("wishlisted", wishlisted, "total", total)));
    }
}
