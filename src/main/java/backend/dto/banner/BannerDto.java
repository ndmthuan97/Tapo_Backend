package backend.dto.banner;

import java.time.Instant;
import java.util.UUID;

public record BannerDto(
        UUID    id,
        String  title,
        String  imageUrl,
        String  linkUrl,
        Integer position,
        Boolean isActive,
        Instant createdAt
) {}
