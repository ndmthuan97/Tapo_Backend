package backend.controller;

import backend.dto.chat.ChatMessageDto;
import backend.dto.chat.SendMessageRequest;
import backend.security.CustomUserDetails;
import backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * STOMP WebSocket message handler for live chat.
 *
 * <p>java-pro: Uses @MessageMapping — clients send to /app/chat/{roomId}/send.
 * ChatServiceImpl handles DB persistence + WS fanout internally.
 *
 * <p>Auth: StompAuthChannelInterceptor ensures JWT is validated on CONNECT.
 * Authentication principal is available on all incoming messages.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final ChatService chatService;

    /**
     * Client sends: STOMP SEND /app/chat/{roomId}/send
     * Server saves message and pushes via:
     *  - /user/{customerId}/queue/chat/{roomId}  → customer receives
     *  - /topic/admin/chat                        → admin panel receives (if sender is customer)
     */
    @MessageMapping("/chat/{roomId}/send")
    public void handleChatMessage(
            @DestinationVariable String roomId,
            @Valid SendMessageRequest request,
            Authentication authentication) {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID senderId = userDetails.getId();

        log.debug("[STOMP Chat] senderId={} roomId={}", senderId, roomId);
        ChatMessageDto dto = chatService.sendMessage(UUID.fromString(roomId), senderId, request.content());
        log.debug("[STOMP Chat] Message persisted: {}", dto.id());
    }
}
