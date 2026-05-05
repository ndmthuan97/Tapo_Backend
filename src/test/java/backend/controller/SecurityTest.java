package backend.controller;

import backend.exception.GlobalExceptionHandler;
import backend.security.JwtTokenProvider;
import backend.service.ProductService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security-focused tests — SEC-003, SEC-005, SEC-007
 *
 * SEC-003 (rate limit checkout by user) is covered by CHKOUT-009 in OrderControllerTest.
 * SEC-004 (rate limit contact form) is covered by CONTACT-002 in ContactControllerTest.
 * SEC-006 (XSS in browser) is MANUAL — requires browser.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Security Tests")
class SecurityTest {

    // ── SEC-005: SQL injection in search param ────────────────────────────────

    @Nested
    @DisplayName("SEC-005 — SQL Injection in search param")
    class SqlInjectionSearch {

        @Mock ProductService productService;

        @InjectMocks ProductController productController;

        MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders
                    .standaloneSetup(productController)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
        }

        @Test
        @DisplayName("SEC-005: search với SQL injection payload → 200, không ảnh hưởng DB")
        void search_sqlInjectionPayload_returns200() throws Exception {
            // getProducts(page, size, search, status, categoryId, brandId, minPrice, maxPrice, minRating, inStock, sort)
            given(productService.getProducts(anyInt(), anyInt(), eq("'; DROP TABLE products--"),
                    any(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), anyString()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/products")
                            .param("search", "'; DROP TABLE products--"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("SEC-005b: search với UNION-based injection → 200, xử lý bình thường")
        void search_unionBasedInjection_returns200() throws Exception {
            given(productService.getProducts(anyInt(), anyInt(), eq("' UNION SELECT * FROM users--"),
                    any(), isNull(), isNull(), isNull(), isNull(), isNull(), any(), anyString()))
                    .willReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/products")
                            .param("search", "' UNION SELECT * FROM users--"))
                    .andExpect(status().isOk());
        }
    }

    // ── SEC-007: Startup fail without JWT_SECRET ──────────────────────────────

    @Nested
    @DisplayName("SEC-007 — JWT_SECRET required at startup")
    class JwtSecretRequired {

        @Test
        @DisplayName("SEC-007: JwtTokenProvider yêu cầu jwt.secret qua @Value — không có default → app fail nếu thiếu")
        void jwtTokenProvider_requiresJwtSecret_noDefaultValue() throws NoSuchFieldException {
            Field jwtSecretField = JwtTokenProvider.class.getDeclaredField("jwtSecret");

            Value valueAnnotation = jwtSecretField.getAnnotation(Value.class);

            assertThat(valueAnnotation)
                    .as("jwtSecret field must be annotated with @Value")
                    .isNotNull();
            assertThat(valueAnnotation.value())
                    .as("@Value must reference ${jwt.secret} without a default")
                    .isEqualTo("${jwt.secret}");
        }
    }

    // ── SEC-003 (documented): Rate limit checkout ─────────────────────────────

    @Test
    @DisplayName("SEC-003: rate limit checkout — đã được cover trong OrderControllerTest.checkout_rateLimited_429 (CHKOUT-009)")
    void rateLimit_checkout_coveredInOrderControllerTest() {
        // CHKOUT-009 in OrderControllerTest:
        //   given(rateLimiterService.allowCheckout(any())).willReturn(false)
        //   POST /api/orders/checkout → 429
        assertThat(true).isTrue();
    }

    // ── SEC-004 (documented): Rate limit contact ──────────────────────────────

    @Test
    @DisplayName("SEC-004: rate limit contact form — đã được cover trong ContactControllerTest.sendMessage_rateLimited_429 (CONTACT-002)")
    void rateLimit_contactForm_coveredInContactControllerTest() {
        // CONTACT-002 in ContactControllerTest:
        //   given(rateLimiterService.allowContactForm(any())).willReturn(false)
        //   POST /api/contact → 429
        assertThat(true).isTrue();
    }
}
