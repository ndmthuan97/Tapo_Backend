package backend.dto.order;

import backend.model.enums.OrderStatus;

import java.time.Instant;

public record OrderStatusHistoryDto(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String note,
        Instant changedAt
) {}
