package backend.dto.order;

import backend.model.enums.OrderStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for bulk-updating multiple orders to the same new status.
 * java-pro: record for immutable DTO; Bean Validation for safety.
 */
public record BulkStatusRequest(
        @NotEmpty List<UUID> orderIds,
        @NotNull  OrderStatus newStatus,
        String note
) {}
