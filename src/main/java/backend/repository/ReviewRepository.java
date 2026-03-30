package backend.repository;

import backend.model.entity.Review;
import backend.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    // ── Public (Customer-facing) ──────────────────────────────────────────────

    @Query("""
        SELECT r FROM Review r
        JOIN FETCH r.user u
        WHERE r.product.id = :productId AND r.status = :status
        ORDER BY r.createdAt DESC
    """)
    Page<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @Query("""
        SELECT COUNT(oi) > 0 FROM OrderItem oi
        JOIN oi.order o
        WHERE o.user.id = :userId
          AND oi.product.id = :productId
          AND o.status = backend.model.enums.OrderStatus.DELIVERED
    """)
    boolean hasUserPurchasedProduct(UUID userId, UUID productId);

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** Paginated list filtered by status (null = all) */
    @Query("""
        SELECT r FROM Review r
        JOIN FETCH r.user u
        JOIN FETCH r.product p
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
    """)
    Page<Review> findAllForAdmin(@Param("status") ReviewStatus status, Pageable pageable);

    /** Single review with all associations for admin detail */
    @Query("""
        SELECT r FROM Review r
        JOIN FETCH r.user u
        JOIN FETCH r.product p
        WHERE r.id = :id
    """)
    Optional<Review> findByIdForAdmin(@Param("id") UUID id);

    /** Count by status — for dashboard badge */
    long countByStatus(ReviewStatus status);
}
