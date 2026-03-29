package backend.dto.brand;

import java.time.Instant;
import java.util.UUID;

public record BrandDto(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        Boolean isVisible,
        Instant createdAt,
        Instant updatedAt
) {}
