package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.wishlist.WishlistItemDto;
import backend.exception.AuthException;
import backend.model.entity.Product;
import backend.model.entity.User;
import backend.model.entity.Wishlist;
import backend.repository.ProductRepository;
import backend.repository.UserRepository;
import backend.repository.WishlistRepository;
import backend.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;

    // ── Mapping ─────────────────────────────────────────────────────────────────

    private WishlistItemDto toDto(Wishlist w) {
        Product p = w.getProduct();
        return new WishlistItemDto(
                w.getId(),
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getThumbnailUrl(),
                p.getPrice(),
                p.getOriginalPrice(),
                p.getStock(),
                p.getAvgRating(),
                p.getReviewCount(),
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                w.getCreatedAt()
        );
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<WishlistItemDto> getWishlist(UUID userId, Pageable pageable) {
        return wishlistRepo.findByUserIdWithProduct(userId, pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public WishlistItemDto addToWishlist(UUID userId, UUID productId) {
        if (wishlistRepo.existsByUserIdAndProductId(userId, productId)) {
            return wishlistRepo.findByUserIdAndProductId(userId, productId)
                    .map(this::toDto)
                    .orElseThrow(() -> new AuthException(CustomCode.NOT_FOUND));
        }

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new AuthException(CustomCode.PRODUCT_NOT_FOUND));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        Wishlist entry = new Wishlist();
        entry.setUser(user);
        entry.setProduct(product);
        return toDto(wishlistRepo.save(entry));
    }

    @Override
    @Transactional
    public void removeFromWishlist(UUID userId, UUID productId) {
        if (!wishlistRepo.existsByUserIdAndProductId(userId, productId)) {
            throw new AuthException(CustomCode.NOT_FOUND);
        }
        wishlistRepo.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWishlisted(UUID userId, UUID productId) {
        return wishlistRepo.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getWishlistCount(UUID userId) {
        return wishlistRepo.countByUserId(userId);
    }
}
