package backend.dto.chat;

import java.time.Instant;

/**
 * DTO for a single chat message — java-pro: Java 21 record.
 */
public record ChatMessageDto(
        String id,
        String roomId,
        String senderId,
        String senderName,
        String senderAvatar,
        String content,
        boolean isRead,
        Instant createdAt
) {}
