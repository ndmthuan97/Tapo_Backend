package backend.service.impl;

import backend.dto.contact.ContactMessageDto;
import backend.dto.contact.SendContactRequest;
import backend.model.entity.ContactMessage;
import backend.repository.ContactMessageRepository;
import backend.service.ContactService;
import backend.service.EmailService;
import backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of ContactService.
 * Handles all business logic for contact form (submit, list, reply).
 *
 * java-pro: @Transactional on write methods; readOnly on reads for HikariCP perf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final ContactMessageRepository contactRepo;
    private final EmailService             emailService;
    private final NotificationService      notificationService;

    // ── Public ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void submit(SendContactRequest request) {
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
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageDto> listAll(int page, int size, boolean unreadOnly) {
        var pageable = PageRequest.of(page, Math.min(size, 50));
        Page<ContactMessage> result = unreadOnly
                ? contactRepo.findByIsReadFalseOrderByCreatedAtDesc(pageable)
                : contactRepo.findAllByOrderByCreatedAtDesc(pageable);
        return result.map(this::toDto);
    }

    @Override
    @Transactional
    public ContactMessageDto markRead(UUID id) {
        var msg = findById(id);
        msg.setRead(true);
        return toDto(contactRepo.save(msg));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread() {
        return contactRepo.countByIsReadFalse();
    }

    @Override
    @Transactional
    public ContactMessageDto reply(UUID id, String content) {
        var msg = findById(id);
        // Send reply email async — never blocks HTTP response
        emailService.sendContactReply(msg.getEmail(), msg.getName(), msg.getTopic(), content);

        if (!msg.isRead()) {
            msg.setRead(true);
            contactRepo.save(msg);
        }
        log.info("[Contact] Admin replied to {} <{}> on topic: {}", msg.getName(), msg.getEmail(), msg.getTopic());
        return toDto(msg);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ContactMessage findById(UUID id) {
        return contactRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn với id: " + id));
    }

    private ContactMessageDto toDto(ContactMessage m) {
        return new ContactMessageDto(
                m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                m.getTopic(), m.getMessage(), m.isRead(), m.getCreatedAt()
        );
    }
}
