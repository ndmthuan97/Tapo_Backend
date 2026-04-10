package backend.service.impl;

import backend.dto.chat.ChatMessageDto;
import backend.dto.chat.ChatRoomDto;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.ChatMessage;
import backend.model.entity.ChatRoom;
import backend.model.entity.User;
import backend.model.enums.ChatRoomStatus;
import backend.model.enums.UserRole;
import backend.repository.ChatMessageRepository;
import backend.repository.ChatRoomRepository;
import backend.repository.UserRepository;
import backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Chat service implementation.
 * java-pro: @Async WS push + @Transactional DB writes fully separated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String ADMIN_CHAT_TOPIC = "/topic/admin/chat";
    private static final String USER_CHAT_QUEUE  = "/queue/chat/";

    private final ChatRoomRepository    chatRoomRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final UserRepository        userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Open or get room ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ChatRoomDto openOrGetRoom(UUID customerId) {
        User customer = findUser(customerId);

        ChatRoom room = chatRoomRepo
                .findByCustomer_IdAndStatus(customerId, ChatRoomStatus.OPEN)
                .orElseGet(() -> {
                    var newRoom = new ChatRoom();
                    newRoom.setCustomer(customer);
                    newRoom.setStatus(ChatRoomStatus.OPEN);
                    return chatRoomRepo.save(newRoom);
                });

        return toRoomDto(room, customerId);
    }

    // ── Send message ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ChatMessageDto sendMessage(UUID roomId, UUID senderId, String content) {
        ChatRoom room = findRoom(roomId);

        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new AppException(CustomCode.CHAT_ROOM_CLOSED);
        }

        User sender = findUser(senderId);
        authorizeRoomAccess(room, sender);

        ChatMessage msg = new ChatMessage();
        msg.setChatRoom(room);
        msg.setSender(sender);
        msg.setContent(content.trim());
        msg.setIsRead(false);
        chatMessageRepo.save(msg);

        ChatMessageDto dto = toMessageDto(msg);

        // Push WS — fire-and-forget async
        pushMessage(room, sender, dto);

        log.info("[Chat] senderId={} → roomId={} ({}chars)", senderId, roomId, content.length());
        return dto;
    }

    // ── Get message history ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID roomId, UUID requesterId, int page, int size) {
        ChatRoom room = findRoom(roomId);
        User requester = findUser(requesterId);
        authorizeRoomAccess(room, requester);

        return chatMessageRepo
                .findByRoomId(roomId, PageRequest.of(page, size, Sort.by("createdAt").ascending()))
                .map(this::toMessageDto)
                .toList();
    }

    // ── Admin: list all rooms ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> listRooms() {
        return chatRoomRepo.findAllWithUsers().stream()
                .map(r -> toRoomDto(r, r.getCustomer().getId()))
                .toList();
    }

    // ── Mark as read ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void markAsRead(UUID roomId, UUID readerId) {
        chatMessageRepo.markAllReadInRoom(roomId, readerId);
    }

    // ── Close room ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ChatRoomDto closeRoom(UUID roomId, UUID adminId) {
        ChatRoom room = findRoom(roomId);
        room.setStatus(ChatRoomStatus.CLOSED);
        chatRoomRepo.save(room);
        log.info("[Chat] Room {} closed by adminId={}", roomId, adminId);
        return toRoomDto(room, room.getCustomer().getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @Async
    protected void pushMessage(ChatRoom room, User sender, ChatMessageDto dto) {
        try {
            // Push to customer's personal queue
            String customerId = room.getCustomer().getId().toString();
            messagingTemplate.convertAndSendToUser(customerId, USER_CHAT_QUEUE + room.getId(), dto);

            // If sender is customer → also notify admin topic
            if (sender.getRole() == UserRole.CUSTOMER) {
                messagingTemplate.convertAndSend(ADMIN_CHAT_TOPIC, dto);
            }
            // If sender is admin/staff → push to customer queue (already done above via room.customer)
        } catch (Exception ex) {
            log.warn("[Chat] WS push failed for room={}: {}", room.getId(), ex.getMessage());
        }
    }

    private ChatRoom findRoom(UUID roomId) {
        return chatRoomRepo.findById(roomId)
                .orElseThrow(() -> new AppException(CustomCode.CHAT_ROOM_NOT_FOUND));
    }

    private User findUser(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new AppException(CustomCode.USER_NOT_FOUND));
    }

    /**
     * Authorize: customer can only access their own room; admin/staff can access any.
     */
    private void authorizeRoomAccess(ChatRoom room, User user) {
        boolean isAdminOrStaff = user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.SALES_STAFF
                || user.getRole() == UserRole.WAREHOUSE_STAFF;
        boolean isOwner        = room.getCustomer().getId().equals(user.getId());
        if (!isAdminOrStaff && !isOwner) {
            throw new AppException(CustomCode.CHAT_NOT_AUTHORIZED);
        }
    }

    private ChatRoomDto toRoomDto(ChatRoom room, UUID viewerId) {
        String lastMsg = null;
        if (!room.getMessages().isEmpty()) {
            lastMsg = room.getMessages().getLast().getContent();
        }
        long unread = chatMessageRepo.countUnread(room.getId(), viewerId);

        return new ChatRoomDto(
                room.getId().toString(),
                room.getCustomer().getId().toString(),
                room.getCustomer().getFullName(),
                room.getCustomer().getAvatarUrl(),
                room.getStaff() != null ? room.getStaff().getId().toString() : null,
                room.getStaff() != null ? room.getStaff().getFullName() : null,
                room.getStatus(),
                lastMsg,
                room.getUpdatedAt(),
                unread,
                room.getCreatedAt()
        );
    }

    private ChatMessageDto toMessageDto(ChatMessage msg) {
        return new ChatMessageDto(
                msg.getId().toString(),
                msg.getChatRoom().getId().toString(),
                msg.getSender().getId().toString(),
                msg.getSender().getFullName(),
                msg.getSender().getAvatarUrl(),
                msg.getContent(),
                msg.getIsRead(),
                msg.getCreatedAt()
        );
    }
}
