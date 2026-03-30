package backend.dto.review;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(
        @NotNull UUID productId,
        @NotNull UUID orderId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment,
        List<String> images
) {}
