package backend.dto.order;

import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full order detail */
public record OrderDto(
        UUID id,
        String orderCode,
        OrderStatus status,
        PaymentStatus paymentStatus,

        // Shipping address snapshot
        String shippingRecipientName,
        String shippingPhone,
        String shippingAddress,
        String shippingDistrict,
        String shippingCity,

        // Financials
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,

        String customerNote,
        List<OrderItemDto> items,
        List<OrderStatusHistoryDto> statusHistory,
        Instant createdAt
) {}
