package backend.model.entity;

import backend.model.enums.ReceiptType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventory_receipts")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReceipt extends BaseEntity {

    @Column(name = "receipt_code", nullable = false, unique = true, length = 30)
    private String receiptCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReceiptType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Optional reference to order for EXPORT type receipts (sold products leaving warehouse).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(columnDefinition = "TEXT")
    private String note;

    // ===== Relationships =====

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryReceiptItem> items = new ArrayList<>();
}
