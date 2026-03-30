package backend.service.impl;

import backend.dto.order.*;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.repository.*;
import backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<OrderStatus> CANCELLABLE = Set.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    private static final BigDecimal SHIPPING_FEE = BigDecimal.valueOf(30_000);

    private final OrderRepository orderRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final AddressRepository addressRepo;

    // ── Mapping helpers ─────────────────────────────────────────────────────────

    private OrderItemDto toItemDto(OrderItem oi) {
        return new OrderItemDto(
                oi.getId(),
                oi.getProduct().getId(),
                oi.getProductName(),
                oi.getProductThumbnail(),
                oi.getUnitPrice().longValue(),
                oi.getQuantity(),
                oi.getTotalPrice()
        );
    }

    private OrderStatusHistoryDto toHistoryDto(OrderStatusHistory h) {
        return new OrderStatusHistoryDto(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getCreatedAt());
    }

    private OrderDto toOrderDto(Order o) {
        return new OrderDto(
                o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(),
                o.getShippingRecipientName(), o.getShippingPhone(),
                o.getShippingAddress(), o.getShippingDistrict(), o.getShippingCity(),
                o.getSubtotal(), o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(),
                o.getCustomerNote(),
                o.getItems().stream().map(this::toItemDto).toList(),
                o.getStatusHistory().stream().map(this::toHistoryDto).toList(),
                o.getCreatedAt()
        );
    }

    private OrderSummary toSummary(Order o) {
        int totalQty = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        String firstName = o.getItems().isEmpty() ? "" : o.getItems().get(0).getProductName();
        String firstThumb = o.getItems().isEmpty() ? null : o.getItems().get(0).getProductThumbnail();
        return new OrderSummary(
                o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(),
                totalQty, o.getTotalAmount(), firstName, firstThumb, o.getCreatedAt()
        );
    }

    // ── Generate unique order code ───────────────────────────────────────────────

    private String generateOrderCode() {
        String code;
        do {
            code = "TP" + System.currentTimeMillis() % 10_000_000_000L;
        } while (orderRepo.existsByOrderCode(code));
        return code;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderDto createOrder(UUID userId, CreateOrderRequest request) {
        // 1. Load cart
        List<CartItem> cartItems = cartItemRepo.findByUserIdWithProduct(userId);
        if (cartItems.isEmpty()) {
            throw new AuthException(CustomCode.CART_EMPTY);
        }

        // 2. Load user & address
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        Address address = addressRepo.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new AuthException(CustomCode.ADDRESS_NOT_FOUND));

        // 3. Validate stock & compute subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            if (p.getStock() < ci.getQuantity()) {
                throw new AuthException(CustomCode.INSUFFICIENT_STOCK);
            }
            subtotal = subtotal.add(
                    p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))
            );
        }

        // 4. Build Order
        Order order = new Order();
        order.setOrderCode(generateOrderCode());
        order.setUser(user);
        order.setShippingRecipientName(address.getRecipientName());
        order.setShippingPhone(address.getPhoneNumber());
        order.setShippingAddress(address.getAddress());
        order.setShippingDistrict(address.getDistrict());
        order.setShippingCity(address.getCity());
        order.setSubtotal(subtotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(SHIPPING_FEE);
        order.setTotalAmount(subtotal.add(SHIPPING_FEE));
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setCustomerNote(request.customerNote());

        // 5. Build OrderItems + deduct stock
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(p);
            oi.setProductName(p.getName());
            oi.setProductThumbnail(p.getThumbnailUrl());
            oi.setUnitPrice(p.getPrice());
            oi.setQuantity(ci.getQuantity());
            oi.setTotalPrice(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            order.getItems().add(oi);

            p.setStock(p.getStock() - ci.getQuantity());
            p.setSoldCount(p.getSoldCount() + ci.getQuantity());
            productRepo.save(p);
        }

        // 6. Initial status history
        OrderStatusHistory hist = new OrderStatusHistory();
        hist.setOrder(order);
        hist.setFromStatus(null);
        hist.setToStatus(OrderStatus.PENDING);
        hist.setNote("Đơn hàng mới được tạo");
        order.getStatusHistory().add(hist);

        Order saved = orderRepo.save(order);

        // 7. Clear cart
        cartItemRepo.deleteAllByUserId(userId);

        return toOrderDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummary> getMyOrders(UUID userId, int page, int size, OrderStatus status) {
        var pageable = PageRequest.of(page, Math.min(size, 20));
        Page<Order> orders = (status != null)
                ? orderRepo.findByUserIdAndStatus(userId, status, pageable)
                : orderRepo.findByUserId(userId, pageable);
        return orders.map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrderDetail(UUID userId, UUID orderId) {
        Order order = orderRepo.findByIdAndUserIdWithDetail(orderId, userId)
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));
        return toOrderDto(order);
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(UUID userId, UUID orderId) {
        Order order = orderRepo.findByIdAndUserIdWithDetail(orderId, userId)
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));

        if (!CANCELLABLE.contains(order.getStatus())) {
            throw new AuthException(CustomCode.ORDER_CANNOT_CANCEL);
        }

        OrderStatus prevStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);

        // Restore stock
        for (OrderItem oi : order.getItems()) {
            Product p = oi.getProduct();
            p.setStock(p.getStock() + oi.getQuantity());
            p.setSoldCount(Math.max(0, p.getSoldCount() - oi.getQuantity()));
            productRepo.save(p);
        }

        // Add history
        OrderStatusHistory hist = new OrderStatusHistory();
        hist.setOrder(order);
        hist.setFromStatus(prevStatus);
        hist.setToStatus(OrderStatus.CANCELLED);
        hist.setNote("Khách hàng hủy đơn");
        order.getStatusHistory().add(hist);

        return toOrderDto(orderRepo.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummary> getAllOrders(int page, int size, OrderStatus status) {
        var pageable = PageRequest.of(page, Math.min(size, 50));
        return orderRepo.findAllByStatusOptional(status, pageable).map(this::toSummary);
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatus(UUID orderId, OrderStatus newStatus, String note) {
        Order order = orderRepo.findByIdWithDetail(orderId)
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));

        OrderStatus prev = order.getStatus();

        // Restore stock if cancelling
        if (newStatus == OrderStatus.CANCELLED && prev != OrderStatus.CANCELLED) {
            for (OrderItem oi : order.getItems()) {
                Product p = oi.getProduct();
                p.setStock(p.getStock() + oi.getQuantity());
                p.setSoldCount(Math.max(0, p.getSoldCount() - oi.getQuantity()));
                productRepo.save(p);
            }
        }

        order.setStatus(newStatus);

        OrderStatusHistory hist = new OrderStatusHistory();
        hist.setOrder(order);
        hist.setFromStatus(prev);
        hist.setToStatus(newStatus);
        hist.setNote(note != null && !note.isBlank() ? note : "Admin cập nhật trạng thái");
        hist.setCreatedAt(Instant.now());
        order.getStatusHistory().add(hist);

        return toOrderDto(orderRepo.save(order));
    }
}
