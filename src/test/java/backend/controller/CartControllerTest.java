package backend.controller;

import backend.dto.cart.AddToCartRequest;
import backend.dto.cart.CartItemDto;
import backend.dto.cart.CartResponse;
import backend.dto.cart.UpdateCartQuantityRequest;
import backend.model.entity.User;
import backend.security.CustomUserDetails;
import backend.service.CartService;
import backend.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CartController Unit Tests — standaloneSetup + SecurityContextHolder injection.
 *
 * Test cases: CART-001, CART-002, CART-003, CART-004, CART-007, CART-008, CART-009
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartController — Unit Tests")
class CartControllerTest {

    @Mock CartService cartService;

    @InjectMocks CartController cartController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    User user;
    CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.activeUser();
        principal = new CustomUserDetails(user);

        // Inject principal into SecurityContext so @AuthenticationPrincipal resolves
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        ));

        mockMvc = MockMvcBuilders
                .standaloneSetup(cartController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private CartResponse emptyCart() {
        return new CartResponse(Collections.emptyList(), 0, BigDecimal.ZERO);
    }

    private CartItemDto cartItemDto(UUID productId, int qty) {
        return new CartItemDto(UUID.randomUUID(), productId, "Test Product", "test-product",
                null, null, null, 100_000L, null, qty, 10, BigDecimal.valueOf(100_000L * qty));
    }

    // ── GET /api/cart ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/cart")
    class GetCart {

        // CART-001
        @Test
        @DisplayName("getCart returns empty cart for authenticated user → 200")
        void getCart_emptyCart_200() throws Exception {
            given(cartService.getCart(user.getId())).willReturn(emptyCart());

            mockMvc.perform(get("/api/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.totalItems").value(0))
                    .andExpect(jsonPath("$.data.subtotal").value(0));
        }

        @Test
        @DisplayName("getCart returns cart with items → 200")
        void getCart_withItems_200() throws Exception {
            UUID productId = UUID.randomUUID();
            CartResponse cart = new CartResponse(
                    Collections.singletonList(cartItemDto(productId, 2)), 1, BigDecimal.valueOf(200_000));
            given(cartService.getCart(user.getId())).willReturn(cart);

            mockMvc.perform(get("/api/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalItems").value(1))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2));
        }
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/cart/items")
    class AddItem {

        // CART-002
        @Test
        @DisplayName("addItem with valid productId and qty=1 → 200 with CartItemDto")
        void addItem_success_200() throws Exception {
            UUID productId = UUID.randomUUID();
            AddToCartRequest request = new AddToCartRequest(productId, 1);
            CartItemDto dto = cartItemDto(productId, 1);
            given(cartService.addItem(eq(user.getId()), any(AddToCartRequest.class))).willReturn(dto);

            mockMvc.perform(post("/api/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.data.quantity").value(1));
        }

        // CART-003 — same product added again: service merges qty, controller just returns updated item
        @Test
        @DisplayName("addItem for existing product → service called, updated item returned")
        void addItem_existingProduct_serviceHandlesMerge() throws Exception {
            UUID productId = UUID.randomUUID();
            AddToCartRequest request = new AddToCartRequest(productId, 1);
            CartItemDto merged = cartItemDto(productId, 3); // e.g. was 2, now 3
            given(cartService.addItem(eq(user.getId()), any())).willReturn(merged);

            mockMvc.perform(post("/api/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantity").value(3));
        }
    }

    // ── PUT /api/cart/items/{productId} ───────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/cart/items/{productId}")
    class UpdateQuantity {

        // CART-004
        @Test
        @DisplayName("updateQuantity to qty=3 → 200 with updated item")
        void updateQuantity_success_200() throws Exception {
            UUID productId = UUID.randomUUID();
            UpdateCartQuantityRequest request = new UpdateCartQuantityRequest(3);
            CartItemDto updated = cartItemDto(productId, 3);
            given(cartService.updateQuantity(eq(user.getId()), eq(productId), any())).willReturn(updated);

            mockMvc.perform(put("/api/cart/items/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantity").value(3));
        }
    }

    // ── DELETE /api/cart/items/{productId} ────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/cart/items/{productId}")
    class RemoveItem {

        // CART-007
        @Test
        @DisplayName("removeItem → 200 with success message")
        void removeItem_success_200() throws Exception {
            UUID productId = UUID.randomUUID();
            willDoNothing().given(cartService).removeItem(user.getId(), productId);

            mockMvc.perform(delete("/api/cart/items/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đã xóa sản phẩm khỏi giỏ hàng"));
        }
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/cart")
    class ClearCart {

        // CART-008
        @Test
        @DisplayName("clearCart → 200 with success message")
        void clearCart_success_200() throws Exception {
            willDoNothing().given(cartService).clearCart(user.getId());

            mockMvc.perform(delete("/api/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đã xóa toàn bộ giỏ hàng"));
        }
    }

    // ── CART-009: unauthenticated (SecurityContext empty) ─────────────────────────

    @Nested
    @DisplayName("Security — unauthenticated access")
    class UnauthenticatedAccess {

        // CART-009 — standaloneSetup without security filter: null principal causes NPE.
        // Full enforcement is validated via SecurityConfig integration test.
        // Here we verify the controller requires a non-null principal.
        @Test
        @DisplayName("CART-009: getCart without principal → NullPointerException (production gives 401 via JWT filter)")
        void getCart_noPrincipal_throws() {
            SecurityContextHolder.clearContext(); // remove principal

            // Without SecurityContext, @AuthenticationPrincipal resolves to null.
            // cartService.getCart(null.getId()) throws NullPointerException.
            // MockMvc re-throws it as a NestedServletException rather than returning a 5xx.
            // In production, JwtAuthFilter intercepts before reaching the controller → 401.
            assertThrows(Exception.class, () -> mockMvc.perform(get("/api/cart")));
        }
    }
}
