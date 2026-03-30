package backend.repository;

import backend.model.entity.ReturnRequest;
import backend.model.enums.ReturnRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    @Query("""
        SELECT rr FROM ReturnRequest rr
        JOIN FETCH rr.order o
        JOIN FETCH rr.user u
        WHERE rr.order.id = :orderId AND rr.user.id = :userId
    """)
    Optional<ReturnRequest> findByOrderIdAndUserId(@Param("orderId") UUID orderId,
                                                    @Param("userId") UUID userId);

    @Query("""
        SELECT rr FROM ReturnRequest rr
        JOIN FETCH rr.order o
        JOIN FETCH rr.user u
        WHERE rr.user.id = :userId
        ORDER BY rr.createdAt DESC
    """)
    Page<ReturnRequest> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    boolean existsByOrderIdAndUserId(UUID orderId, UUID userId);

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Query("""
        SELECT rr FROM ReturnRequest rr
        JOIN FETCH rr.order o
        JOIN FETCH rr.user u
        WHERE (:status IS NULL OR rr.status = :status)
        ORDER BY rr.createdAt DESC
    """)
    Page<ReturnRequest> findAllForAdmin(@Param("status") ReturnRequestStatus status, Pageable pageable);
}
