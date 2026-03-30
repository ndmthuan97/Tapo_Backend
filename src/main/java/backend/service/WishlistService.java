package backend.service;

import backend.dto.wishlist.WishlistItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WishlistService {

    Page<WishlistItemDto> getWishlist(UUID userId, Pageable pageable);

    WishlistItemDto addToWishlist(UUID userId, UUID productId);

    void removeFromWishlist(UUID userId, UUID productId);

    boolean isWishlisted(UUID userId, UUID productId);

    long getWishlistCount(UUID userId);
}
