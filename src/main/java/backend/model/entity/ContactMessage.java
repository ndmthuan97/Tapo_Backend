package backend.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persists contact form submissions.
 * Read-only from frontend — admin reads via admin panel.
 * (java-pro: minimal entity, no bi-directional associations needed)
 */
@Entity
@Table(name = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /** Message body — max 2000 chars */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Whether the message has been read/handled by admin */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;
}
