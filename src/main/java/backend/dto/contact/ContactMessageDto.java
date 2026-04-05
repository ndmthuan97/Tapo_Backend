package backend.dto.contact;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for contact message — used by admin list view.
 * Java 21 record pattern.
 */
public record ContactMessageDto(
        UUID    id,
        String  name,
        String  email,
        String  phone,
        String  topic,
        String  message,
        boolean isRead,
        Instant createdAt
) {}
