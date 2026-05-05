package backend.controller;

import backend.dto.cart.UpdateCartQuantityRequest;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.GlobalExceptionHandler;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CartController validation & stock edge-case tests — with GlobalExceptionHandler.
 * Covers: CART-005, CART-006
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartController — Validation Tests")
class CartControllerValidationTest {

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

        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        ));

        mockMvc = MockMvcBuilders
                .standaloneSetup(cartController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── CART-005: số lượng vượt tồn kho ──────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/cart/items/{productId} — stock constraints")
    class StockConstraints {

        @Test
        @DisplayName("CART-005: số lượng vượt tồn kho → 400 INSUFFICIENT_STOCK")
        void updateQuantity_exceedsStock_400() throws Exception {
            UUID productId = UUID.randomUUID();
            given(cartService.updateQuantity(eq(user.getId()), eq(productId), any()))
                    .willThrow(new AppException(CustomCode.INSUFFICIENT_STOCK));

            String body = objectMapper.writeValueAsString(new UpdateCartQuantityRequest(999));

            mockMvc.perform(put("/api/cart/items/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CART-006: addItem với qty âm → service throws → 400")
        void addItem_negativeQuantity_400() throws Exception {
            UUID productId = UUID.randomUUID();
            given(cartService.addItem(eq(user.getId()), any()))
                    .willThrow(new AppException(CustomCode.INSUFFICIENT_STOCK));

            String body = """
                    {"productId":"%s","quantity":-1}
                    """.formatted(productId);

            mockMvc.perform(post("/api/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CART-006b: updateQuantity qty=0 → service throws → 400")
        void updateQuantity_zero_400() throws Exception {
            UUID productId = UUID.randomUUID();
            given(cartService.updateQuantity(eq(user.getId()), eq(productId), any()))
                    .willThrow(new AppException(CustomCode.INSUFFICIENT_STOCK));

            String body = objectMapper.writeValueAsString(new UpdateCartQuantityRequest(0));

            mockMvc.perform(put("/api/cart/items/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
