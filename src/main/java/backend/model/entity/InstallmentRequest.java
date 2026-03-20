package backend.model.entity;

import backend.model.enums.InstallmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "installment_requests")
@Getter
@Setter
@NoArgsConstructor
public class InstallmentRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal productPrice;

    /** Installment period in months: 6, 12, 18, 24 */
    @Column(name = "installment_months", nullable = false)
    private Integer installmentMonths;

    @Column(name = "monthly_payment", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "down_payment", precision = 15, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "staff_note", columnDefinition = "TEXT")
    private String staffNote;
}
