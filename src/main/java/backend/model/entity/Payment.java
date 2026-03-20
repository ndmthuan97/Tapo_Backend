package backend.model.entity;

import backend.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "payos_order_code", unique = true)
    private Long payosOrderCode;

    @Column(name = "payos_payment_link_id", length = 255)
    private String payosPaymentLinkId;

    @Column(name = "payment_link_url", length = 500)
    private String paymentLinkUrl;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * PayOS transaction code returned on successful payment.
     */
    @Column(name = "transaction_id", length = 255)
    private String transactionId;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;
}
