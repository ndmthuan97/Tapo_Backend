package backend.dto.product;

import java.util.UUID;

/**
 * DTO for a single product image in the gallery.
 * java-pro: immutable record pattern.
 */
public record ProductImageDto(
        UUID   id,
        String imageUrl,
        String altText,
        int    sortOrder
) {}
