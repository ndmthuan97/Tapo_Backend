package backend.controller;

import backend.dto.contact.ContactMessageDto;
import backend.dto.contact.SendContactRequest;
import backend.exception.GlobalExceptionHandler;
import backend.service.ContactService;
import backend.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ContactController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: CONTACT-001 ~ CONTACT-004, CONTACT-006
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContactController — Unit Tests")
class ContactControllerTest {

    @Mock ContactService contactService;
    @Mock RateLimiterService rateLimiterService;

    @InjectMocks ContactController contactController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(contactController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContactMessageDto stubContactDto(boolean isRead) {
        return new ContactMessageDto(
                UUID.randomUUID(), "Nguyen Van A", "user@tapo.vn",
                "0901234567", "Hỗ trợ sản phẩm",
                "Tôi cần hỗ trợ về sản phẩm laptop.",
                isRead, Instant.now()
        );
    }

    // ── POST /api/contact ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/contact")
    class SendContactForm {

        @Test
        @DisplayName("CONTACT-001: gửi contact form hợp lệ → 201")
        void sendMessage_valid_201() throws Exception {
            given(rateLimiterService.allowContactForm(any())).willReturn(true);
            willDoNothing().given(contactService).submit(any(SendContactRequest.class));

            String body = """
                    {
                      "name": "Nguyen Van A",
                      "email": "user@tapo.vn",
                      "subject": "Hỗ trợ sản phẩm",
                      "message": "Tôi cần hỗ trợ về sản phẩm laptop."
                    }
                    """;

            mockMvc.perform(post("/api/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Tin nhắn đã được gửi thành công"));
        }

        @Test
        @DisplayName("CONTACT-002: rate limit contact form → 429")
        void sendMessage_rateLimited_429() throws Exception {
            given(rateLimiterService.allowContactForm(any())).willReturn(false);

            String body = """
                    {
                      "name": "Bot",
                      "email": "bot@tapo.vn",
                      "subject": "Spam",
                      "message": "Spam message..."
                    }
                    """;

            mockMvc.perform(post("/api/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("CONTACT-003: thiếu message → 400 validation error")
        void sendMessage_missingMessage_400() throws Exception {
            given(rateLimiterService.allowContactForm(any())).willReturn(true);

            String body = """
                    {
                      "name": "Nguyen Van A",
                      "email": "user@tapo.vn",
                      "subject": "Hỗ trợ"
                    }
                    """;

            mockMvc.perform(post("/api/contact")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /api/admin/contact/{id}/read ─────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/admin/contact/{id}/read")
    class MarkRead {

        @Test
        @DisplayName("CONTACT-004: Admin đọc message → 200, isRead=true")
        void markRead_200() throws Exception {
            UUID messageId = UUID.randomUUID();
            ContactMessageDto read = stubContactDto(true);
            given(contactService.markRead(messageId)).willReturn(read);

            mockMvc.perform(put("/api/admin/contact/{id}/read", messageId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isRead").value(true));
        }
    }

    // ── GET /api/admin/contact/unread-count ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/contact/unread-count")
    class UnreadCount {

        @Test
        @DisplayName("CONTACT-006: unread count → 200, số đúng")
        void getUnreadCount_200() throws Exception {
            given(contactService.countUnread()).willReturn(5L);

            mockMvc.perform(get("/api/admin/contact/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(5));
        }

        @Test
        @DisplayName("CONTACT-006b: unread count sau markRead → số giảm")
        void getUnreadCount_afterMarkRead_decreases() throws Exception {
            UUID msgId = UUID.randomUUID();
            // Before mark read: 5 unread
            given(contactService.markRead(msgId)).willReturn(stubContactDto(true));
            given(contactService.countUnread()).willReturn(4L); // decreased

            // Mark as read
            mockMvc.perform(put("/api/admin/contact/{id}/read", msgId))
                    .andExpect(status().isOk());

            // Check count decreased
            mockMvc.perform(get("/api/admin/contact/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(4));
        }
    }

    // ── GET /api/admin/contact ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/contact")
    class GetAllMessages {

        @Test
        @DisplayName("CONTACT admin list → 200, tất cả messages phân trang")
        void getAllMessages_200() throws Exception {
            Page<ContactMessageDto> page = new PageImpl<>(List.of(
                    stubContactDto(false),
                    stubContactDto(true)
            ));
            given(contactService.listAll(anyInt(), anyInt(), anyBoolean())).willReturn(page);

            mockMvc.perform(get("/api/admin/contact"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }
    }
}
