package backend.service;

import backend.dto.common.CustomCode;
import backend.dto.order.BulkStatusRequest;
import backend.dto.order.CreateOrderRequest;
import backend.dto.order.OrderDto;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.repository.*;
import backend.service.*;
import backend.service.impl.OrderServiceImpl;
import backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Unit Tests")
class OrderServiceImplTest {

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
        // InventoryService dùng @Lazy @Autowired — không trong constructor → inject thủ công
        ReflectionTestUtils.setField(orderService, "inventoryService", inventoryService);
    }


    // CREATE ORDER
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createOrder()")
    class CreateOrderTests {

        @Test
        @DisplayName("createOrder success → stock decremented, cart cleared, order status PENDING")
        void createOrder_success() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(10);
            CartItem cartItem = TestDataFactory.cartItem(user, product, 2);
            Address address = TestDataFactory.address(user);

            CreateOrderRequest req = TestDataFactory.createOrderRequest(address.getId());

            given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
            given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
            given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));
            // Note: no voucherRepo stub when voucherCode is null — avoids UnnecessaryStubbing

            Order savedOrder = TestDataFactory.pendingOrder(user);
            given(orderRepo.save(any(Order.class))).willReturn(savedOrder);

            OrderDto result = orderService.createOrder(user.getId(), req);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
            // Stock decremented
            assertThat(product.getStock()).isEqualTo(8);
            verify(cartItemRepo).deleteAllByUserId(user.getId());
        }

        @Test
        @DisplayName("createOrder with empty cart → throw CART_EMPTY")
        void createOrder_cartEmpty() {
            User user = TestDataFactory.activeUser();
            given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(Collections.emptyList());

            CreateOrderRequest req = TestDataFactory.createOrderRequest(UUID.randomUUID());

            assertThatThrownBy(() -> orderService.createOrder(user.getId(), req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getCustomCode())
                            .isEqualTo(CustomCode.CART_EMPTY));
        }

        @Test
        @DisplayName("createOrder insufficient stock → throw INSUFFICIENT_STOCK")
        void createOrder_insufficientStock() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(1); // only 1 in stock
            CartItem cartItem = TestDataFactory.cartItem(user, product, 5); // buying 5
            Address address = TestDataFactory.address(user);

            given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
            given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
            given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));

            CreateOrderRequest req = TestDataFactory.createOrderRequest(address.getId());

            assertThatThrownBy(() -> orderService.createOrder(user.getId(), req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getCustomCode())
                            .isEqualTo(CustomCode.INSUFFICIENT_STOCK));
        }

        @Test
        @DisplayName("createOrder with valid voucher → discount deducted from total")
        void createOrder_withVoucher_appliesDiscount() {
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
            given(voucherService.applyVoucher(eq(voucher), any(BigDecimal.class)))
                    .willReturn(BigDecimal.valueOf(50_000));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            Order savedOrder = TestDataFactory.pendingOrder(user);
            given(orderRepo.save(captor.capture())).willReturn(savedOrder);

            CreateOrderRequest req = TestDataFactory.createOrderRequest(address.getId(), "SAVE50K");
            orderService.createOrder(user.getId(), req);

            Order captured = captor.getValue();
            assertThat(captured.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
        }

        @Test
        @DisplayName("createOrder with null paymentMethod → defaults to COD")
        void createOrder_nullPaymentMethod_defaultsCod() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(10);
            CartItem cartItem = TestDataFactory.cartItem(user, product, 1);
            Address address = TestDataFactory.address(user);

            // Request with null paymentMethod
            CreateOrderRequest req = new CreateOrderRequest(address.getId(), null, null, null);

            given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
            given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
            given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            given(orderRepo.save(captor.capture())).willReturn(TestDataFactory.pendingOrder(user));

            orderService.createOrder(user.getId(), req);

            assertThat(captor.getValue().getPaymentMethod()).isEqualTo("COD");
        }

        @Test
        @DisplayName("createOrder shippingFee fixed at 30,000")
        void createOrder_shippingFeeIs30000() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(10);
            CartItem cartItem = TestDataFactory.cartItem(user, product, 1);
            Address address = TestDataFactory.address(user);

            given(cartItemRepo.findByUserIdWithProduct(user.getId())).willReturn(List.of(cartItem));
            given(userRepo.findById(user.getId())).willReturn(Optional.of(user));
            given(addressRepo.findByIdAndUserId(address.getId(), user.getId())).willReturn(Optional.of(address));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            given(orderRepo.save(captor.capture())).willReturn(TestDataFactory.pendingOrder(user));

            orderService.createOrder(user.getId(), TestDataFactory.createOrderRequest(address.getId()));

            assertThat(captor.getValue().getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CANCEL ORDER
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrderTests {

        @Test
        @DisplayName("cancelOrder from PENDING → CANCELLED, stock restored")
        void cancelOrder_fromPending() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(8);
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PENDING);

            OrderItem item = buildOrderItem(order, product, 2);
            order.getItems().add(item);

            given(orderRepo.findByIdAndUserIdWithDetail(order.getId(), user.getId()))
                    .willReturn(Optional.of(order));
            given(orderRepo.save(any())).willReturn(order);

            OrderDto result = orderService.cancelOrder(user.getId(), order.getId(), "Changed mind");

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(product.getStock()).isEqualTo(10); // 8 + 2 restored
        }

        @Test
        @DisplayName("cancelOrder from CONFIRMED → CANCELLED")
        void cancelOrder_fromConfirmed() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(5);
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.CONFIRMED);
            order.getItems().add(buildOrderItem(order, product, 1));

            given(orderRepo.findByIdAndUserIdWithDetail(order.getId(), user.getId()))
                    .willReturn(Optional.of(order));
            given(orderRepo.save(any())).willReturn(order);

            OrderDto result = orderService.cancelOrder(user.getId(), order.getId(), null);
            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancelOrder from PROCESSING → throw ORDER_CANNOT_CANCEL")
        void cancelOrder_fromProcessing_notAllowed() {
            User user = TestDataFactory.activeUser();
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PROCESSING);

            given(orderRepo.findByIdAndUserIdWithDetail(order.getId(), user.getId()))
                    .willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), order.getId(), null))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.ORDER_CANNOT_CANCEL));
        }

        @Test
        @DisplayName("cancelOrder for non-existent order → throw ORDER_NOT_FOUND")
        void cancelOrder_notFound() {
            User user = TestDataFactory.activeUser();
            UUID orderId = UUID.randomUUID();

            given(orderRepo.findByIdAndUserIdWithDetail(orderId, user.getId()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), orderId, null))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getCustomCode())
                            .isEqualTo(CustomCode.ORDER_NOT_FOUND));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // UPDATE ORDER STATUS (Admin)
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateOrderStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("updateOrderStatus → DELIVERED → autoExportOnDelivery called")
        void updateStatus_toDelivered_callsInventoryExport() {
            User user = TestDataFactory.activeUser();
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.PROCESSING);

            given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
            given(orderRepo.save(any())).willReturn(order);

            orderService.updateOrderStatus(order.getId(), OrderStatus.DELIVERED, null);

            verify(inventoryService).autoExportOnDelivery(order);
        }

        @Test
        @DisplayName("updateOrderStatus → CANCELLED → stock restored for all items")
        void updateStatus_toCancelled_restoresStock() {
            User user = TestDataFactory.activeUser();
            Product product = TestDataFactory.product(0); // stock 0 after sale
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.CONFIRMED);
            order.getItems().add(buildOrderItem(order, product, 3));

            given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
            given(orderRepo.save(any())).willReturn(order);

            orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED, "Out of stock");

            assertThat(product.getStock()).isEqualTo(3); // 0 + 3 restored
        }

        @Test
        @DisplayName("updateOrderStatus sends WebSocket notification to customer")
        void updateStatus_sendsWebSocketNotification() {
            User user = TestDataFactory.activeUser();
            Order order = TestDataFactory.orderWithStatus(user, OrderStatus.CONFIRMED);

            given(orderRepo.findByIdWithDetail(order.getId())).willReturn(Optional.of(order));
            given(orderRepo.save(any())).willReturn(order);

            orderService.updateOrderStatus(order.getId(), OrderStatus.PROCESSING, null);

            verify(notificationService).notifyUserOrderStatusChanged(
                    eq(user.getId()), anyString(), eq(OrderStatus.PROCESSING.name()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BULK UPDATE STATUS
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("bulkUpdateStatus()")
    class BulkUpdateTests {

        @Test
        @DisplayName("bulkUpdate 1 valid + 1 non-existent → only 1 updated")
        void bulkUpdate_partialSuccess() {
            User user = TestDataFactory.activeUser();
            UUID validId = UUID.randomUUID();
            UUID invalidId = UUID.randomUUID();

            Order validOrder = TestDataFactory.orderWithStatus(user, OrderStatus.CONFIRMED);
            validOrder.setId(validId);

            given(orderRepo.findByIdWithDetail(validId)).willReturn(Optional.of(validOrder));
            given(orderRepo.findByIdWithDetail(invalidId)).willReturn(Optional.empty());
            given(orderRepo.save(any())).willReturn(validOrder);

            BulkStatusRequest req = new BulkStatusRequest(
                    List.of(validId, invalidId), OrderStatus.PROCESSING, null);

            List<String> updated = orderService.bulkUpdateStatus(req);

            assertThat(updated).hasSize(1);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private OrderItem buildOrderItem(Order order, Product product, int qty) {
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
