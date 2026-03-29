package backend.dto.order;

import backend.model.enums.OrderStatus;
import backend.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight order info for list views */
public record OrderSummary(
        UUID id,
        String orderCode,
        OrderStatus status,
        PaymentStatus paymentStatus,
        int totalQty,
        BigDecimal totalAmount,
        String firstProductName,
        String firstProductThumbnail,
        Instant createdAt
) {}
