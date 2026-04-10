package backend.controller;

import backend.dto.chat.ChatMessageDto;
import backend.dto.chat.ChatRoomDto;
import backend.dto.chat.SendMessageRequest;
import backend.dto.common.ApiResponse;
import backend.security.CustomUserDetails;
import backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "API cho Customer-Admin live chat")
public class ChatController {

    private final ChatService chatService;

    // ── Customer endpoints ────────────────────────────────────────────────────

    @PostMapping("/rooms")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mở hoặc lấy phòng chat đang mở của customer")
    public ResponseEntity<ApiResponse<ChatRoomDto>> openRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ChatRoomDto room = chatService.openOrGetRoom(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Chat room ready", room));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy lịch sử tin nhắn của phòng chat")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ChatMessageDto> messages = chatService.getMessages(roomId, userDetails.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi tin nhắn (REST fallback — prefer STOMP /app/chat/{roomId}/send)")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ChatMessageDto msg = chatService.sendMessage(roomId, userDetails.getId(), request.content());
        return ResponseEntity.ok(ApiResponse.success("Message sent", msg));
    }

    @PostMapping("/rooms/{roomId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc tất cả tin nhắn trong phòng")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        chatService.markAsRead(roomId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    // ── Admin/Staff endpoints ─────────────────────────────────────────────────

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_STAFF','WAREHOUSE_STAFF')")
    @Operation(summary = "Admin: danh sách tất cả phòng chat")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> listRooms() {
        return ResponseEntity.ok(ApiResponse.success(chatService.listRooms()));
    }

    @PostMapping("/rooms/{roomId}/close")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_STAFF','WAREHOUSE_STAFF')")
    @Operation(summary = "Admin: đóng phòng chat")
    public ResponseEntity<ApiResponse<ChatRoomDto>> closeRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ChatRoomDto closed = chatService.closeRoom(roomId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Chat room closed", closed));
    }
}
