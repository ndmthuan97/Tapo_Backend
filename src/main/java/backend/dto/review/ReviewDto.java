package backend.dto.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID userId,
        String userName,
        String userAvatar,
        Integer rating,
        String comment,
        List<String> images,
        Instant createdAt
) {}
