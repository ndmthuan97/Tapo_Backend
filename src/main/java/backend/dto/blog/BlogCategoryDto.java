package backend.dto.blog;

import java.util.UUID;

public record BlogCategoryDto(
        UUID id,
        String name,
        String slug,
        String description,
        Integer sortOrder
) {}
