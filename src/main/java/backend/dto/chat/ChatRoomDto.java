package backend.dto.chat;

import backend.model.enums.ChatRoomStatus;
import java.time.Instant;

/**
 * DTO for a chat room — java-pro: Java 21 record, immutable.
 */
public record ChatRoomDto(
        String id,
        String customerId,
        String customerName,
        String customerAvatar,
        String staffId,
        String staffName,
        ChatRoomStatus status,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt
) {}
