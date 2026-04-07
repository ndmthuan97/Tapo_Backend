package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.contact.ContactMessageDto;
import backend.dto.contact.SendContactRequest;
import backend.service.ContactService;
import backend.service.RateLimiterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Contact form API — thin controller delegating to ContactService.
 *
 * java-pro: controller should only handle HTTP concerns (routing, status codes,
 * request parsing). All business logic lives in ContactService / ContactServiceImpl.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Liên hệ & thông báo người dùng")
public class ContactController {

    private final ContactService     contactService;
    private final RateLimiterService rateLimiterService;

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * Submit contact form — no authentication required.
     */
    @PostMapping("/api/contact")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @Valid @RequestBody SendContactRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = resolveClientIp(httpRequest);
        if (!rateLimiterService.allowContactForm(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(429, "Bạn đã gửi quá nhiều tin nhắn. Vui lòng thử lại sau 1 phút."));
        }
        contactService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tin nhắn đã được gửi thành công", null));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/admin/contact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContactMessageDto>>> getAllMessages(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "20")    int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                contactService.listAll(page, size, unreadOnly)
        ));
    }

    @PutMapping("/api/admin/contact/{id}/read")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ContactMessageDto>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.markRead(id)));
    }

    @GetMapping("/api/admin/contact/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(contactService.countUnread()));
    }

    @PostMapping("/api/admin/contact/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ContactMessageDto>> replyToMessage(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyRequest body
    ) {
        return ResponseEntity.ok(ApiResponse.success("Phản hồi đã được gửi",
                contactService.reply(id, body.content())));
    }

    /** DTO for reply request body */
    record ReplyRequest(
            @NotBlank @Size(min = 5, max = 3000) String content
    ) {}

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Extract real client IP — handles X-Forwarded-For from reverse proxy. */
    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
