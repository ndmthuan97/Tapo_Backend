package backend.service.impl;

import backend.dto.order.*;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.model.entity.*;
import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;
import backend.repository.*;
import backend.service.EmailService;
import backend.service.InventoryService;
import backend.service.NotificationService;
import backend.service.OrderService;
import backend.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private final VoucherRepository voucherRepo;
    private final VoucherService voucherService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // @Lazy prevents circular bean dependency: Order → Inventory → (no Order)
    @Lazy
    @Autowired
    private InventoryService inventoryService;

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
                o.getPaymentMethod(),
                o.getShippingRecipientName(), o.getShippingPhone(),
                o.getShippingAddress(), o.getShippingDistrict(), o.getShippingCity(),
                o.getSubtotal(), o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(),
                o.getCustomerNote(), o.getCancelReason(),
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
    // Dùng UUID hexString thay vì millis % N để đảm bảo uniqueness mà không cần
    // DB round-trip trong vòng lặp (tránh race condition và potential infinite loop)
    private String generateOrderCode() {
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
        // Lấy 10 ký tự đầu của UUID → collision probability ≈ 1/16^10 ≈ cực nhỏ
        return "TP" + uuid.substring(0, 10).toUpperCase();
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderDto createOrder(UUID userId, CreateOrderRequest request) {
        // 1. Load cart
        List<CartItem> cartItems = cartItemRepo.findByUserIdWithProduct(userId);
        if (cartItems.isEmpty()) {
            throw new AppException(CustomCode.CART_EMPTY);
        }

        // 2. Load user & address
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(CustomCode.USER_NOT_FOUND));

        Address address = addressRepo.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new AppException(CustomCode.ADDRESS_NOT_FOUND));

        // 3. Validate stock & compute subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            if (p.getStock() < ci.getQuantity()) {
                throw new AppException(CustomCode.INSUFFICIENT_STOCK);
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
        // shippingDistrict intentionally NOT set — district removed after VN 2025 admin reform
        order.setShippingCity(address.getCity());
        order.setSubtotal(subtotal);
        order.setShippingFee(SHIPPING_FEE);

        // 4b. Apply voucher if provided
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            backend.model.entity.Voucher voucher = voucherRepo
                    .findByCode(request.voucherCode().trim().toUpperCase())
                    .orElseThrow(() -> new AuthException(CustomCode.VOUCHER_NOT_FOUND));
            discountAmount = voucherService.applyVoucher(voucher, subtotal);
            order.setVoucher(voucher);
        }

        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(subtotal.subtract(discountAmount).add(SHIPPING_FEE).max(BigDecimal.ZERO));
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        // Normalize paymentMethod: default COD nếu không gửi lên
        String method = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod().toUpperCase()
                : "COD";
        order.setPaymentMethod(method);
        order.setCustomerNote(request.customerNote());

        // 5. Build OrderItems + deduct stock (batch update để tránh N+1)
        List<Product> productsToUpdate = new java.util.ArrayList<>();
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
            productsToUpdate.add(p);
        }
        productRepo.saveAll(productsToUpdate);

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

        // 8. Send async email + WS notification — both @Async, không block transaction
        emailService.sendOrderConfirmation(saved);
        notificationService.notifyNewOrder(
                saved.getOrderCode(),
                saved.getShippingRecipientName(),
                saved.getTotalAmount().longValue()
        );

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
    public OrderDto cancelOrder(UUID userId, UUID orderId, String reason) {
        Order order = orderRepo.findByIdAndUserIdWithDetail(orderId, userId)
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));

        if (!CANCELLABLE.contains(order.getStatus())) {
            throw new AuthException(CustomCode.ORDER_CANNOT_CANCEL);
        }

        OrderStatus prevStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);

        // Restore stock (batch)
        List<Product> toRestore = new java.util.ArrayList<>();
        for (OrderItem oi : order.getItems()) {
            Product p = oi.getProduct();
            p.setStock(p.getStock() + oi.getQuantity());
            p.setSoldCount(Math.max(0, p.getSoldCount() - oi.getQuantity()));
            toRestore.add(p);
        }
        productRepo.saveAll(toRestore);

        // Add history
        OrderStatusHistory hist = new OrderStatusHistory();
        hist.setOrder(order);
        hist.setFromStatus(prevStatus);
        hist.setToStatus(OrderStatus.CANCELLED);
        hist.setNote(reason != null && !reason.isBlank() ? "Khách hàng hủy đơn: " + reason : "Khách hàng hủy đơn");
        order.getStatusHistory().add(hist);
        
        if (reason != null && !reason.isBlank()) {
            order.setCancelReason(reason);
        }

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
                .orElseThrow(() -> new AppException(CustomCode.ORDER_NOT_FOUND));

        OrderStatus prev = order.getStatus();

        // Restore stock if cancelling (batch)
        if (newStatus == OrderStatus.CANCELLED && prev != OrderStatus.CANCELLED) {
            List<Product> stockToRestore = new java.util.ArrayList<>();
            for (OrderItem oi : order.getItems()) {
                Product p = oi.getProduct();
                p.setStock(p.getStock() + oi.getQuantity());
                p.setSoldCount(Math.max(0, p.getSoldCount() - oi.getQuantity()));
                stockToRestore.add(p);
            }
            productRepo.saveAll(stockToRestore);
        }

        order.setStatus(newStatus);

        // java-pro: Auto-generate EXPORT receipt when order reaches DELIVERED
        // Called before save() — order.getItems() is already loaded (eager in findByIdWithDetail)
        if (newStatus == OrderStatus.DELIVERED && prev != OrderStatus.DELIVERED) {
            inventoryService.autoExportOnDelivery(order);
        }

        OrderStatusHistory hist = new OrderStatusHistory();
        hist.setOrder(order);
        hist.setFromStatus(prev);
        hist.setToStatus(newStatus);
        hist.setNote(note != null && !note.isBlank() ? note : "Admin cập nhật trạng thái");
        hist.setCreatedAt(Instant.now());
        order.getStatusHistory().add(hist);

        Order saved = orderRepo.save(order);

        // Fire-and-forget WS push to the customer
        notificationService.notifyUserOrderStatusChanged(
                saved.getUser().getId(), saved.getOrderCode(), newStatus.name());

        return toOrderDto(saved);
    }

    @Override
    @Transactional
    public List<String> bulkUpdateStatus(BulkStatusRequest request) {
        List<String> updatedCodes = new java.util.ArrayList<>();
        for (UUID id : request.orderIds()) {
            try {
                OrderDto dto = updateOrderStatus(id, request.newStatus(), request.note());
                updatedCodes.add(dto.orderCode());
            } catch (Exception ignored) {
                // Skip invalid/not-found orders — partial success allowed
            }
        }
        return updatedCodes;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto adminGetOrderDetail(UUID orderId) {
        Order order = orderRepo.findByIdWithDetail(orderId)
                .orElseThrow(() -> new AppException(CustomCode.ORDER_NOT_FOUND));
        return toOrderDto(order);
    }
}
