package backend.dto.order;

public record CancelOrderRequest(
        String cancelReason
) {}
