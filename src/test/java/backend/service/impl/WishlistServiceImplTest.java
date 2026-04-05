package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.Product;
import backend.model.entity.User;
import backend.model.entity.Wishlist;
import backend.repository.ProductRepository;
import backend.repository.UserRepository;
import backend.repository.WishlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistServiceImpl — Unit Tests")
class WishlistServiceImplTest {

    @Mock WishlistRepository wishlistRepo;
    @Mock ProductRepository  productRepo;
    @Mock UserRepository     userRepo;

    @InjectMocks WishlistServiceImpl wishlistService;

    // ── addToWishlist ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addToWishlist: new item → saves entry, returns DTO")
    void addToWishlist_newItem_savesEntryAndReturnsDto() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product  = stubProduct(productId);
        User    user     = stubUser(userId);
        Wishlist entry   = stubWishlist(user, product);

        given(wishlistRepo.existsByUserIdAndProductId(userId, productId)).willReturn(false);
        given(productRepo.findById(productId)).willReturn(Optional.of(product));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));
        given(wishlistRepo.save(any())).willReturn(entry);

        var dto = wishlistService.addToWishlist(userId, productId);

        then(wishlistRepo).should().save(any(Wishlist.class));
        assertThat(dto).isNotNull();
        assertThat(dto.productId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("addToWishlist: already exists → returns existing without duplicate save")
    void addToWishlist_alreadyExists_returnsExistingEntry() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = stubProduct(productId);
        User    user    = stubUser(userId);
        Wishlist existing = stubWishlist(user, product);

        given(wishlistRepo.existsByUserIdAndProductId(userId, productId)).willReturn(true);
        given(wishlistRepo.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(existing));

        var dto = wishlistService.addToWishlist(userId, productId);

        then(wishlistRepo).should(never()).save(any());
        assertThat(dto.productId()).isEqualTo(productId);
    }

    // ── removeFromWishlist ────────────────────────────────────────────────────

    @Test
    @DisplayName("removeFromWishlist: exists → calls deleteByUserIdAndProductId")
    void removeFromWishlist_exists_deletesEntry() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        given(wishlistRepo.existsByUserIdAndProductId(userId, productId)).willReturn(true);

        wishlistService.removeFromWishlist(userId, productId);

        then(wishlistRepo).should().deleteByUserIdAndProductId(userId, productId);
    }

    @Test
    @DisplayName("removeFromWishlist: not in wishlist → throws NOT_FOUND")
    void removeFromWishlist_notExists_throwsAuthException() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        given(wishlistRepo.existsByUserIdAndProductId(userId, productId)).willReturn(false);

        assertThatThrownBy(() -> wishlistService.removeFromWishlist(userId, productId))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining(CustomCode.NOT_FOUND.getDefaultMessage());

        then(wishlistRepo).should(never()).deleteByUserIdAndProductId(any(), any());
    }

    // ── isWishlisted ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isWishlisted: delegates to repository existsByUserIdAndProductId")
    void isWishlisted_delegatesToRepository() {
        UUID userId    = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(wishlistRepo.existsByUserIdAndProductId(userId, productId)).willReturn(true);

        assertThat(wishlistService.isWishlisted(userId, productId)).isTrue();

        then(wishlistRepo).should().existsByUserIdAndProductId(userId, productId);
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private Product stubProduct(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Laptop");
        p.setSlug("test-laptop");
        p.setPrice(BigDecimal.valueOf(15_000_000));
        p.setOriginalPrice(BigDecimal.valueOf(18_000_000));
        p.setStock(10);
        p.setAvgRating(BigDecimal.valueOf(4.5));
        p.setReviewCount(8);
        return p;
    }

    private User stubUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Test User");
        return u;
    }

    private Wishlist stubWishlist(User user, Product product) {
        Wishlist w = new Wishlist();
        w.setId(UUID.randomUUID());
        w.setUser(user);
        w.setProduct(product);
        return w;
    }
}
