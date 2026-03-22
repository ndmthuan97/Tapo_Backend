package backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        String slug,

        String description,

        String imageUrl,

        Integer sortOrder,

        Boolean isVisible
) {}
