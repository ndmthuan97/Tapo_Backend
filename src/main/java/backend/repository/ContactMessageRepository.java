package backend.repository;

import backend.model.entity.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    /** List all messages, newest first */
    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** List only unread messages */
    Page<ContactMessage> findByIsReadFalseOrderByCreatedAtDesc(Pageable pageable);

    /** Count unread — for admin badge */
    long countByIsReadFalse();
}
