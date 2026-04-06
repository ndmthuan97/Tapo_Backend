package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.contact.ContactMessageDto;
import backend.dto.contact.SendContactRequest;
import backend.model.entity.ContactMessage;
import backend.repository.ContactMessageRepository;
import backend.service.EmailService;
import backend.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Contact form API.
 * Public endpoint: POST /api/contact  — no auth required (公開)
 * Admin endpoints: ADMIN/STAFF only
 * (java-pro: thin controller, business logic in service layer minimal here since domain is simple)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Liên hệ & thông báo người dùng")
public class ContactController {

    private final ContactMessageRepository contactRepo;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * Submit contact form — no authentication required.
     * Rate limiting should be applied at reverse-proxy level in production.
     */
    @PostMapping("/api/contact")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @Valid @RequestBody SendContactRequest request
    ) {
        ContactMessage msg = new ContactMessage();
        msg.setName(request.name().trim());
        msg.setEmail(request.email().trim().toLowerCase());
        msg.setPhone(request.phone() != null ? request.phone().trim() : null);
        msg.setTopic(request.topic().trim());
        msg.setMessage(request.message().trim());
        contactRepo.save(msg);

        log.info("[Contact] New message from {} <{}>: {}", msg.getName(), msg.getEmail(), msg.getTopic());
        // Notify admin in realtime via WebSocket — fire-and-forget
        notificationService.notifyNewContactMessage(msg.getName(), msg.getTopic());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tin nhắn đã được gửi thành công", null));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/contact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContactMessageDto>>> getAllMessages(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 50));
        Page<ContactMessage> result = unreadOnly
                ? contactRepo.findByIsReadFalseOrderByCreatedAtDesc(pageable)
                : contactRepo.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(ApiResponse.success(result.map(this::toDto)));
    }

    @PutMapping("/api/admin/contact/{id}/read")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ContactMessageDto>> markRead(
            @PathVariable UUID id
    ) {
        var msg = contactRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn"));
        msg.setRead(true);
        return ResponseEntity.ok(ApiResponse.success(toDto(contactRepo.save(msg))));
    }

    @GetMapping("/api/admin/contact/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(contactRepo.countByIsReadFalse()));
    }

    /**
     * Admin reply to a contact message via email.
     * Auto-marks message as read after reply.
     */
    @PostMapping("/api/admin/contact/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ContactMessageDto>> replyToMessage(
            @PathVariable UUID id,
            @RequestBody @Valid ReplyRequest body
    ) {
        var msg = contactRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn"));

        // Send reply email async — never blocks HTTP response
        emailService.sendContactReply(msg.getEmail(), msg.getName(), msg.getTopic(), body.content());

        // Auto-mark as read
        if (!msg.isRead()) {
            msg.setRead(true);
            contactRepo.save(msg);
        }

        log.info("[Contact] Admin replied to {} <{}> on topic: {}", msg.getName(), msg.getEmail(), msg.getTopic());
        return ResponseEntity.ok(ApiResponse.success("Phản hồi đã được gửi", toDto(msg)));
    }

    /** DTO for reply request body */
    record ReplyRequest(
            @NotBlank @Size(min = 5, max = 3000) String content
    ) {}

    // ── Helper ────────────────────────────────────────────────────────────────

    private ContactMessageDto toDto(ContactMessage m) {
        return new ContactMessageDto(
                m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                m.getTopic(), m.getMessage(), m.isRead(), m.getCreatedAt()
        );
    }
}
