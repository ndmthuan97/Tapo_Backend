package backend.dto.brand;

import backend.model.enums.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100)
        String name,

        String slug,

        String logoUrl,

        CatalogStatus status
) {}
