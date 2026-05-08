package backend.dto.product;

import java.util.UUID;

/** Lightweight reference used in dropdowns (category/brand selectors). */
public record SimpleRefDto(UUID id, String name, String slug) {}
