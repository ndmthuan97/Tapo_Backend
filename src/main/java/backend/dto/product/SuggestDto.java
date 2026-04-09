package backend.dto.product;

import java.math.BigDecimal;
import java.util.UUID;

public record SuggestDto(
        UUID id,
        String name,
        String slug,
        String thumbnailUrl,
        BigDecimal price
) {}
