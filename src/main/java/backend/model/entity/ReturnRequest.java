package backend.model.entity;

import backend.model.enums.ReturnRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /**
     * List of image URLs uploaded as evidence for the return request, stored as JSONB array.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_images", columnDefinition = "jsonb")
    private List<String> evidenceImages;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnRequestStatus status = ReturnRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "staff_note", columnDefinition = "TEXT")
    private String staffNote;
}
