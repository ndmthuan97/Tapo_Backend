package backend.controller;

import backend.dto.order.CreateOrderRequest;
import backend.dto.order.OrderSummary;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.service.OrderService;
import backend.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController Unit Test — standaloneSetup.
 * Endpoints requiring @AuthenticationPrincipal need Spring Security context —
 * only admin list endpoints (no principal) are tested here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController — Unit Tests")
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock RateLimiterService rateLimiterService;

    @InjectMocks
    OrderController orderController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders")
    class AdminOrders {

        @Test
        @DisplayName("getAllOrders → 200 with page data")
        void getAllOrders_200() throws Exception {
            Page<OrderSummary> emptyPage = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(orderService.getAllOrders(anyInt(), anyInt(), any())).willReturn(emptyPage);

            mockMvc.perform(get("/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("getAllOrders with status filter → 200")
        void getAllOrders_withStatusFilter_200() throws Exception {
            Page<OrderSummary> emptyPage = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(orderService.getAllOrders(anyInt(), anyInt(), any())).willReturn(emptyPage);

            mockMvc.perform(get("/api/admin/orders")
                            .param("status", OrderStatus.PENDING.name()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getAllOrders pagination page=1&size=5 → 200")
        void getAllOrders_pagination_200() throws Exception {
            Page<OrderSummary> emptyPage = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(1, 5), 0);
            given(orderService.getAllOrders(anyInt(), anyInt(), any())).willReturn(emptyPage);

            mockMvc.perform(get("/api/admin/orders")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk());
        }
    }
}
