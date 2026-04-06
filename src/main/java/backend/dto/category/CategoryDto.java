package backend.dto.category;

import backend.model.enums.CatalogStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
        Integer sortOrder,
        CatalogStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
