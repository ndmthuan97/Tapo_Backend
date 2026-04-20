package backend.flow;

import backend.dto.order.CreateOrderRequest;
import backend.dto.order.OrderDto;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.repository.*;
import backend.service.*;
import backend.service.impl.OrderServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Flow Test: Order Lifecycle
 *
 * Flow: CART → CREATE_ORDER → CONFIRM → PROCESS → DELIVER
 *                                              ↘ CANCEL (from PENDING)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Flow — Order Lifecycle")
class OrderFlowTest {

    @Mock OrderRepository orderRepo;
    @Mock CartItemRepository cartItemRepo;
    @Mock ProductRepository productRepo;
    @Mock UserRepository userRepo;
    @Mock AddressRepository addressRepo;
    @Mock VoucherRepository voucherRepo;
    @Mock VoucherService voucherService;
    @Mock EmailService emailService;
    @Mock NotificationService notificationService;
    @Mock InventoryService inventoryService;

    @InjectMocks
    OrderServiceImpl orderService;

    @BeforeEach
    void injectLazyBeans() {
        ReflectionTestUtils.setField(orderService, "inventoryService", inventoryService);
    }

    // ─── CREATE ORDER ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Create order → stock decremented, cart cleared, order status PENDING")
    void createOrder_stockDeductedAndCartCleared() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(10);
        CartItem cartItem = TestDataFactory.cartItem(user, product, 2);
        Address address = TestDataFactory.address(user);
        CreateOrderRequest req = TestDataFactory.createOrderRequest(address.getId());

        given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));
        given(orderRepo.save(any(backend.model.entity.Order.class))).willReturn(TestDataFactory.pendingOrder(user));

        OrderDto result = orderService.createOrder(user.getId(), req);

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getStock()).isEqualTo(8); // 10 - 2
        verify(cartItemRepo).deleteAllByUserId(user.getId());
    }

    @Test
    @DisplayName("1b. Create order with empty cart → throw CART_EMPTY, stock unchanged")
    void createOrder_emptyCart_throwsCartEmpty() {
        User user = TestDataFactory.activeUser();
        given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(Collections.emptyList());

        assertThatThrownBy(() -> orderService.createOrder(user.getId(),
                TestDataFactory.createOrderRequest(UUID.randomUUID())))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCustomCode())
                        .isEqualTo(CustomCode.CART_EMPTY));

        verifyNoInteractions(orderRepo);
    }

    @Test
    @DisplayName("1c. Create order with insufficient stock → throw INSUFFICIENT_STOCK, stock unchanged")
    void createOrder_insufficientStock_throwsError() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(1); // only 1 in stock
        CartItem cartItem = TestDataFactory.cartItem(user, product, 3); // buying 3
        Address address = TestDataFactory.address(user);

        given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));

        assertThatThrownBy(() -> orderService.createOrder(user.getId(),
                TestDataFactory.createOrderRequest(address.getId())))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCustomCode())
                        .isEqualTo(CustomCode.INSUFFICIENT_STOCK));

        assertThat(product.getStock()).isEqualTo(1); // not decremented
    }

    @Test
    @DisplayName("1d. Create order with voucher → discount applied to totalAmount")
    void createOrder_withVoucher_discountApplied() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(10);
        product.setPrice(BigDecimal.valueOf(1_000_000));
        CartItem cartItem = TestDataFactory.cartItem(user, product, 1);
        Address address = TestDataFactory.address(user);
        Voucher voucher = TestDataFactory.fixedVoucher();

        given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
        given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
        given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));
        given(voucherRepo.findByCode("SAVE50K")).willReturn(Optional.of(voucher));
        given(voucherService.applyVoucher(eq(voucher), any())).willReturn(BigDecimal.valueOf(50_000));

        var captor = org.mockito.ArgumentCaptor.forClass(backend.model.entity.Order.class);
        given(orderRepo.save(captor.capture())).willReturn(TestDataFactory.pendingOrder(user));

        orderService.createOrder(user.getId(), TestDataFactory.createOrderRequest(address.getId(), "SAVE50K"));

        backend.model.entity.Order captured = captor.getValue();
        assertThat(captured.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    // ─── STATE MACHINE TRANSITIONS ────────────────────────────────────────────────

    @Test
    @DisplayName("2. PENDING → CONFIRMED → customer notification sent")
    void confirmOrder_notifiesCustomer() {
        User user = TestDataFactory.activeUser();
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PENDING);

        given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
        given(orderRepo.save(any())).willReturn(order);

        orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(notificationService).notifyUserOrderStatusChanged(
                eq(user.getId()), anyString(), eq(OrderStatus.CONFIRMED.name()));
    }

    @Test
    @DisplayName("3. CONFIRMED → PROCESSING → WebSocket notification sent")
    void processOrder_notifiesViaWebSocket() {
        User user = TestDataFactory.activeUser();
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.CONFIRMED);

        given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
        given(orderRepo.save(any())).willReturn(order);

        orderService.updateOrderStatus(order.getId(), OrderStatus.PROCESSING, null);

        verify(notificationService).notifyUserOrderStatusChanged(
                eq(user.getId()), anyString(), eq(OrderStatus.PROCESSING.name()));
    }

    @Test
    @DisplayName("4. PROCESSING → DELIVERED → autoExportOnDelivery triggered")
    void deliverOrder_triggersInventoryExport() {
        User user = TestDataFactory.activeUser();
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PROCESSING);

        given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
        given(orderRepo.save(any())).willReturn(order);

        orderService.updateOrderStatus(order.getId(), OrderStatus.DELIVERED, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(inventoryService).autoExportOnDelivery(order);
    }

    // ─── CANCEL ORDER ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5. Cancel PENDING order → stock restored to original quantity")
    void cancelFromPending_restoresStock() {
        User user = TestDataFactory.activeUser();
        Product product = TestDataFactory.product(8);
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PENDING);

        OrderItem item = buildOrderItem(order, product, 2);
        order.getItems().add(item);

        given(orderRepo.findByIdAndUserIdWithDetail(order.getId(), user.getId()))
                .willReturn(Optional.of(order));
        given(orderRepo.save(any())).willReturn(order);

        OrderDto result = orderService.cancelOrder(user.getId(), order.getId(), "Changed my mind");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(10); // 8 + 2 = 10 (restored)
    }

    @Test
    @DisplayName("5b. Cancel PROCESSING order → throw ORDER_CANNOT_CANCEL")
    void cancelFromProcessing_notAllowed() {
        User user = TestDataFactory.activeUser();
        backend.model.entity.Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PROCESSING);

        given(orderRepo.findByIdAndUserIdWithDetail(order.getId(), user.getId()))
                .willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), order.getId(), null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                        .isEqualTo(CustomCode.ORDER_CANNOT_CANCEL));
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    private OrderItem buildOrderItem(backend.model.entity.Order order, Product product, int qty) {
        OrderItem oi = new OrderItem();
        oi.setId(UUID.randomUUID());
        oi.setOrder(order);
        oi.setProduct(product);
        oi.setProductName(product.getName());
        oi.setProductThumbnail(product.getThumbnailUrl());
        oi.setUnitPrice(product.getPrice());
        oi.setQuantity(qty);
        oi.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        return oi;
    }
}
