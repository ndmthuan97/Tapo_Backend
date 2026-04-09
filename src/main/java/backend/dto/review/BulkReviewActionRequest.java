package backend.dto.review;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body cho PATCH /api/admin/reviews/bulk-action
 * action = "APPROVE" | "REJECT"
 */
public record BulkReviewActionRequest(
        @NotEmpty(message = "Danh sách review không được rỗng")
        List<UUID> reviewIds,

        @NotNull(message = "Action không được null")
        BulkAction action
) {
    public enum BulkAction { APPROVE, REJECT }
}
