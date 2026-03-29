package backend.dto.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
        Integer sortOrder,
        Boolean isVisible,
        Instant createdAt,
        Instant updatedAt
) {}
