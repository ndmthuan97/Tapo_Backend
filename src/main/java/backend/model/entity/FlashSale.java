package backend.model.entity;

import backend.model.enums.FlashSaleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "flash_sales")
@Getter
@Setter
@NoArgsConstructor
public class FlashSale extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sale_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "stock_limit", nullable = false)
    private Integer stockLimit;

    @Column(name = "sold_count", nullable = false)
    private Integer soldCount = 0;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashSaleStatus status = FlashSaleStatus.SCHEDULED;
}
