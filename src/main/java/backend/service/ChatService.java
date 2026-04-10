package backend.service;

import backend.dto.chat.ChatMessageDto;
import backend.dto.chat.ChatRoomDto;

import java.util.List;
import java.util.UUID;

/**
 * Chat service — manages customer-admin real-time chat rooms.
 *
 * <p>java-pro: interface segregation — chat logic fully decoupled from other domains.
 */
public interface ChatService {

    /**
     * Customer opens (or retrieves) their active OPEN chat room.
     * One active room per customer at a time.
     */
    ChatRoomDto openOrGetRoom(UUID customerId);

    /**
     * Send a message in a room. Persists to DB and pushes via STOMP.
     *
     * @param roomId   target room
     * @param senderId the authenticated user sending the message
     * @param content  message text
     * @return the persisted message DTO
     */
    ChatMessageDto sendMessage(UUID roomId, UUID senderId, String content);

    /**
     * Paginated message history for a room.
     *
     * @param roomId   the room
     * @param page     0-based page index
     * @param size     messages per page
     * @param requesterId the caller — used for authorization check
     */
    List<ChatMessageDto> getMessages(UUID roomId, UUID requesterId, int page, int size);

    /** Admin: list all chat rooms. */
    List<ChatRoomDto> listRooms();

    /** Mark all messages in a room as read (from perspective of readerId). */
    void markAsRead(UUID roomId, UUID readerId);

    /**
     * Admin closes a chat room — moves status to CLOSED.
     *
     * @param roomId    room to close
     * @param adminId   admin performing the action
     */
    ChatRoomDto closeRoom(UUID roomId, UUID adminId);
}
