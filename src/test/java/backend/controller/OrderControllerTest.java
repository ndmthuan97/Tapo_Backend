package backend.controller;

import backend.dto.common.CustomCode;
import backend.dto.order.*;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.exception.GlobalExceptionHandler;
import backend.model.entity.User;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.security.CustomUserDetails;
import backend.service.OrderService;
import backend.service.RateLimiterService;
import backend.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: ORDER-001~006, ORDER-010~015, CHKOUT-004, CHKOUT-009
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController — Unit Tests")
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock RateLimiterService rateLimiterService;

    @InjectMocks OrderController orderController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    User user;
    CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.activeUser();
        principal = new CustomUserDetails(user);

        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        ));

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(orderController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrderDto stubOrderDto(UUID id, OrderStatus status) {
        return new OrderDto(
                id, "TP-" + id.toString().substring(0, 8).toUpperCase(),
                status, PaymentStatus.UNPAID, "COD",
                "Nguyen Van A", "0901234567", "123 Nguyen Hue", null, "Ho Chi Minh",
                BigDecimal.valueOf(500_000), BigDecimal.ZERO,
                BigDecimal.valueOf(30_000), BigDecimal.valueOf(530_000),
                null, null, Collections.emptyList(), Collections.emptyList(), Instant.now()
        );
    }

    private OrderSummary stubOrderSummary(UUID id, OrderStatus status) {
        return new OrderSummary(
                id, "TP-" + id.toString().substring(0, 8).toUpperCase(),
                status, PaymentStatus.UNPAID, 1,
                BigDecimal.valueOf(530_000), "Product A", null, Instant.now()
        );
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders")
    class GetMyOrders {

        @Test
        @DisplayName("ORDER-001: xem danh sách đơn của mình → 200, chỉ đơn của user đó")
        void getMyOrders_200() throws Exception {
            var items = List.of(stubOrderSummary(UUID.randomUUID(), OrderStatus.PENDING));
            Page<OrderSummary> page = new PageImpl<>(items, PageRequest.of(0, 10), items.size());
            given(orderService.getMyOrders(eq(user.getId()), anyInt(), anyInt(), any()))
                    .willReturn(page);

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("ORDER-006: lọc đơn theo status=SHIPPING → 200")
        void getMyOrders_filterByStatus_200() throws Exception {
            var items = List.of(stubOrderSummary(UUID.randomUUID(), OrderStatus.SHIPPING));
            Page<OrderSummary> page = new PageImpl<>(items, PageRequest.of(0, 10), items.size());
            given(orderService.getMyOrders(eq(user.getId()), anyInt(), anyInt(), eq(OrderStatus.SHIPPING)))
                    .willReturn(page);

            mockMvc.perform(get("/api/orders").param("status", "SHIPPING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("SHIPPING"));
        }
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetOrderDetail {

        @Test
        @DisplayName("ORDER-002: xem chi tiết đơn hàng của mình → 200")
        void getOrderDetail_own_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.getOrderDetail(user.getId(), orderId))
                    .willReturn(stubOrderDto(orderId, OrderStatus.PENDING));

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("ORDER-003: xem đơn của người khác → 403")
        void getOrderDetail_otherUser_403() throws Exception {
            UUID othersOrderId = UUID.randomUUID();
            given(orderService.getOrderDetail(user.getId(), othersOrderId))
                    .willThrow(new AuthException(CustomCode.FORBIDDEN));

            mockMvc.perform(get("/api/orders/{id}", othersOrderId))
                    .andExpect(status().isForbidden());
        }
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/orders — checkout")
    class CreateOrder {

        @Test
        @DisplayName("CHKOUT-004: địa chỉ không tồn tại → 404 ADDRESS_NOT_FOUND")
        void createOrder_addressNotFound_404() throws Exception {
            given(rateLimiterService.allowCheckout(any())).willReturn(true);
            given(orderService.createOrder(eq(user.getId()), any()))
                    .willThrow(new AppException(CustomCode.ADDRESS_NOT_FOUND));

            String body = objectMapper.writeValueAsString(
                    new CreateOrderRequest(UUID.randomUUID(), null, null, "COD"));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("CHKOUT-009: rate limit checkout → 429")
        void createOrder_rateLimited_429() throws Exception {
            given(rateLimiterService.allowCheckout(any())).willReturn(false);

            String body = objectMapper.writeValueAsString(
                    new CreateOrderRequest(UUID.randomUUID(), null, null, "COD"));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ── PUT /api/orders/{id}/cancel ───────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/orders/{id}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("ORDER-004: huỷ đơn PENDING → 200, status=CANCELLED")
        void cancelOrder_pending_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.cancelOrder(eq(user.getId()), eq(orderId), any()))
                    .willReturn(stubOrderDto(orderId, OrderStatus.CANCELLED));

            mockMvc.perform(put("/api/orders/{id}/cancel", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cancelReason\":\"Đổi ý\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.message").value("Đã hủy đơn hàng"));
        }

        @Test
        @DisplayName("ORDER-005: huỷ đơn đã CONFIRMED → 400 ORDER_CANNOT_CANCEL")
        void cancelOrder_confirmed_400() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.cancelOrder(eq(user.getId()), eq(orderId), any()))
                    .willThrow(new AuthException(CustomCode.ORDER_CANNOT_CANCEL));

            mockMvc.perform(put("/api/orders/{id}/cancel", orderId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders")
    class AdminOrders {

        @Test
        @DisplayName("ORDER-010: Admin xem tất cả đơn hàng → 200")
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

    // ── PUT /api/admin/orders/{id}/status ─────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/orders/{id}/status")
    class UpdateOrderStatus {

        @Test
        @DisplayName("ORDER-011: Admin cập nhật status đơn → 200")
        void updateOrderStatus_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.CONFIRMED), any()))
                    .willReturn(stubOrderDto(orderId, OrderStatus.CONFIRMED));

            mockMvc.perform(put("/api/admin/orders/{id}/status", orderId)
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.message").value("Cập nhật trạng thái thành công"));
        }

        @Test
        @DisplayName("ORDER-012: cập nhật status với note → 200")
        void updateOrderStatus_withNote_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.SHIPPING), eq("Đã giao GHTK")))
                    .willReturn(stubOrderDto(orderId, OrderStatus.SHIPPING));

            mockMvc.perform(put("/api/admin/orders/{id}/status", orderId)
                            .param("status", "SHIPPING")
                            .param("note", "Đã giao GHTK"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SHIPPING"));
        }
    }

    // ── GET /api/admin/orders/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/orders/{id}")
    class AdminGetOrderDetail {

        @Test
        @DisplayName("ORDER-014: Admin xem chi tiết bất kỳ đơn → 200")
        void adminGetOrderDetail_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.adminGetOrderDetail(orderId))
                    .willReturn(stubOrderDto(orderId, OrderStatus.DELIVERED));

            mockMvc.perform(get("/api/admin/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.data.status").value("DELIVERED"));
        }

        @Test
        @DisplayName("ORDER-014b: Admin xem đơn không tồn tại → 404")
        void adminGetOrderDetail_notFound_404() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.adminGetOrderDetail(orderId))
                    .willThrow(new AppException(CustomCode.ORDER_NOT_FOUND));

            mockMvc.perform(get("/api/admin/orders/{id}", orderId))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PATCH /api/admin/orders/bulk-status ───────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/orders/bulk-status")
    class BulkUpdateStatus {

        @Test
        @DisplayName("ORDER-013: Bulk update status → 200")
        void bulkUpdateStatus_200() throws Exception {
            List<UUID> orderIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<String> updated = orderIds.stream().map(UUID::toString).toList();
            given(orderService.bulkUpdateStatus(any())).willReturn(updated);

            String body = objectMapper.writeValueAsString(
                    new BulkStatusRequest(orderIds, OrderStatus.CONFIRMED, "Xác nhận loạt"));

            mockMvc.perform(patch("/api/admin/orders/bulk-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }
}
