package backend.service;

import backend.dto.contact.ContactMessageDto;
import backend.dto.contact.SendContactRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Contact message service — business logic layer.
 * Extracted from ContactController to comply with layered architecture.
 *
 * java-pro: thin controller, fat service principle.
 */
public interface ContactService {

    /** Submit a new contact message from public form. */
    void submit(SendContactRequest request);

    /** List all contact messages (admin). */
    Page<ContactMessageDto> listAll(int page, int size, boolean unreadOnly);

    /** Mark a message as read. */
    ContactMessageDto markRead(UUID id);

    /** Get count of unread messages. */
    long countUnread();

    /** Send reply email to user and mark message as read. */
    ContactMessageDto reply(UUID id, String content);
}
