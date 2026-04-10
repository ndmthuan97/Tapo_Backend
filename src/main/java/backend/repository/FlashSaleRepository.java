package backend.repository;

import backend.model.entity.FlashSale;
import backend.model.enums.FlashSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, UUID> {

    List<FlashSale> findAllByOrderByStartTimeDesc();

    List<FlashSale> findByStatus(FlashSaleStatus status);

    /**
     * Active flash sales: status=ACTIVE and within time window.
     * Used by public API and front-end countdown banner.
     */
    @Query("""
        SELECT fs FROM FlashSale fs
        JOIN FETCH fs.product
        WHERE fs.status = 'ACTIVE'
          AND fs.startTime <= :now
          AND fs.endTime >= :now
          AND fs.soldCount < fs.stockLimit
        ORDER BY fs.endTime ASC
    """)
    List<FlashSale> findActiveNow(Instant now);

    /** Scheduled flash sales ready to be activated (startTime <= now and SCHEDULED). */
    @Query("""
        SELECT fs FROM FlashSale fs
        WHERE fs.status = 'SCHEDULED'
          AND fs.startTime <= :now
    """)
    List<FlashSale> findDueToActivate(Instant now);

    /** Active flash sales that should be expired (endTime <= now). */
    @Query("""
        SELECT fs FROM FlashSale fs
        WHERE fs.status = 'ACTIVE'
          AND fs.endTime <= :now
    """)
    List<FlashSale> findDueToExpire(Instant now);
}
