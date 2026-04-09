package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.order.CreateOrderRequest;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.repository.*;
import backend.service.EmailService;
import backend.service.NotificationService;
import backend.service.VoucherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl — Unit Tests")
class OrderServiceImplTest {

    @Mock OrderRepository   orderRepo;
    @Mock CartItemRepository cartItemRepo;
    @Mock ProductRepository  productRepo;
    @Mock UserRepository     userRepo;
    @Mock AddressRepository  addressRepo;
    @Mock VoucherRepository  voucherRepo;
    @Mock VoucherService     voucherService;
    @Mock EmailService       emailService;
    @Mock NotificationService notificationService;

    @InjectMocks OrderServiceImpl orderService;

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder: empty cart → throws CART_EMPTY")
    void createOrder_emptyCart_throwsCartEmpty() {
        UUID userId = UUID.randomUUID();
        var request = new CreateOrderRequest(UUID.randomUUID(), null, null, null);

        given(cartItemRepo.findByUserIdWithProduct(userId)).willReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(CustomCode.CART_EMPTY.getDefaultMessage());

        then(orderRepo).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("createOrder: insufficient stock → throws INSUFFICIENT_STOCK")
    void createOrder_insufficientStock_throwsException() {
        UUID    userId  = UUID.randomUUID();
        UUID    addrId  = UUID.randomUUID();
        Product product = stubProduct(5);           // only 5 in stock
        CartItem ci     = stubCartItem(product, 10); // wants 10

        given(cartItemRepo.findByUserIdWithProduct(userId)).willReturn(List.of(ci));
        given(userRepo.findById(userId)).willReturn(Optional.of(stubUser(userId)));
        given(addressRepo.findByIdAndUserId(addrId, userId))
                .willReturn(Optional.of(stubAddress(addrId)));

        // (addressId, customerNote, voucherCode, paymentMethod)
        var request = new CreateOrderRequest(addrId, null, null, "COD");

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(CustomCode.INSUFFICIENT_STOCK.getDefaultMessage());
    }

    @Test
    @DisplayName("createOrder: no paymentMethod → defaults to COD")
    void createOrder_noPaymentMethod_defaultsToCod() {
        UUID    userId  = UUID.randomUUID();
        UUID    addrId  = UUID.randomUUID();
        Product product = stubProduct(10);
        CartItem ci     = stubCartItem(product, 2);
        Order savedOrder = stubOrder(userId);

        given(cartItemRepo.findByUserIdWithProduct(userId)).willReturn(List.of(ci));
        given(userRepo.findById(userId)).willReturn(Optional.of(stubUser(userId)));
        given(addressRepo.findByIdAndUserId(addrId, userId))
                .willReturn(Optional.of(stubAddress(addrId)));
        given(productRepo.saveAll(any())).willReturn(List.of(product));
        given(orderRepo.save(any())).willReturn(savedOrder);
        willDoNothing().given(emailService).sendOrderConfirmation(any());
        willDoNothing().given(notificationService)
                .notifyNewOrder(anyString(), anyString(), anyLong());

        // paymentMethod = null → should default to "COD" (addressId, customerNote, voucherCode, paymentMethod)
        var request = new CreateOrderRequest(addrId, null, null, null);
        orderService.createOrder(userId, request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepo).should().save(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo("COD");
    }

    @Test
    @DisplayName("createOrder: with voucher code → applies discount via VoucherService")
    void createOrder_withVoucherCode_appliesDiscount() {
        UUID    userId  = UUID.randomUUID();
        UUID    addrId  = UUID.randomUUID();
        Product product = stubProduct(10);
        CartItem ci     = stubCartItem(product, 1);
        Voucher  voucher = stubVoucher();
        Order   saved   = stubOrder(userId);

        given(cartItemRepo.findByUserIdWithProduct(userId)).willReturn(List.of(ci));
        given(userRepo.findById(userId)).willReturn(Optional.of(stubUser(userId)));
        given(addressRepo.findByIdAndUserId(addrId, userId))
                .willReturn(Optional.of(stubAddress(addrId)));
        given(voucherRepo.findByCode("SAVE10"))
                .willReturn(Optional.of(voucher));
        given(voucherService.applyVoucher(eq(voucher), any()))
                .willReturn(BigDecimal.valueOf(500_000));
        given(productRepo.saveAll(any())).willReturn(List.of(product));
        given(orderRepo.save(any())).willReturn(saved);
        willDoNothing().given(emailService).sendOrderConfirmation(any());
        willDoNothing().given(notificationService)
                .notifyNewOrder(anyString(), anyString(), anyLong());

        // (addressId, customerNote, voucherCode, paymentMethod)
        var request = new CreateOrderRequest(addrId, null, "SAVE10", "COD");
        orderService.createOrder(userId, request);

        then(voucherService).should().applyVoucher(eq(voucher), any());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepo).should().save(captor.capture());
        assertThat(captor.getValue().getDiscountAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(500_000));
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder: non-cancellable status → throws ORDER_CANNOT_CANCEL")
    void cancelOrder_delivered_throwsCannotCancelException() {
        UUID userId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order  = stubOrder(userId);
        order.setStatus(OrderStatus.DELIVERED);        // not cancellable

        given(orderRepo.findByIdAndUserIdWithDetail(orderId, userId))
                .willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId, "changed my mind"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining(CustomCode.ORDER_CANNOT_CANCEL.getDefaultMessage());
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private Product stubProduct(int stock) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("Test Laptop");
        p.setPrice(BigDecimal.valueOf(10_000_000));
        p.setStock(stock);
        p.setSoldCount(0);
        return p;
    }

    private CartItem stubCartItem(Product product, int qty) {
        CartItem ci = new CartItem();
        ci.setProduct(product);
        ci.setQuantity(qty);
        return ci;
    }

    private User stubUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Nguyen Van A");
        return u;
    }

    private Address stubAddress(UUID id) {
        Address a = new Address();
        a.setId(id);
        a.setRecipientName("Nguyen Van A");
        a.setPhoneNumber("0912345678");
        a.setAddress("123 Nguyen Trai, Phuong Thanh Xuan");
        a.setCity("Ha Noi");
        return a;
    }

    private Order stubOrder(UUID userId) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderCode("TP12345678AB");
        o.setStatus(OrderStatus.PENDING);
        o.setPaymentStatus(PaymentStatus.UNPAID);
        o.setSubtotal(BigDecimal.valueOf(10_000_000));
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setShippingFee(BigDecimal.valueOf(30_000));
        o.setTotalAmount(BigDecimal.valueOf(10_030_000));
        o.setPaymentMethod("COD");
        o.setShippingRecipientName("Nguyen Van A");
        o.setShippingPhone("0912345678");
        return o;
    }

    private Voucher stubVoucher() {
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setCode("SAVE10");
        return v;
    }
}
