package backend.dto.brand;

import backend.model.enums.CatalogStatus;

import java.time.Instant;
import java.util.UUID;

public record BrandDto(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        CatalogStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
