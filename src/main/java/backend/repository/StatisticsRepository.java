package backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import backend.model.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Statistics repository — ALL queries use native PostgreSQL SQL.
 *
 * <p>Rationale: JPQL with Hibernate 6 has edge-case compatibility issues
 * (enum string literals, fully-qualified entity names, FUNCTION() calls).
 * Native SQL is explicit, predictable, and directly testable in psql.
 */
@Repository
public interface StatisticsRepository extends JpaRepository<Order, UUID> {

    // ── Revenue ──────────────────────────────────────────────────────────────

    /** Tổng doanh thu các đơn DELIVERED trong khoảng thời gian */
    @Query(value = """
            SELECT COALESCE(SUM(o.total_amount), 0)
            FROM orders o
            WHERE o.status = 'DELIVERED'
              AND o.created_at >= :from
              AND o.created_at <= :to
            """, nativeQuery = true)
    BigDecimal getTotalRevenue(@Param("from") Instant from,
                               @Param("to")   Instant to);

    /** Doanh thu theo tháng trong năm — EXTRACT() native PostgreSQL */
    @Query(value = """
            SELECT EXTRACT(MONTH FROM o.created_at)::int   AS month,
                   COALESCE(SUM(o.total_amount), 0)         AS revenue,
                   COUNT(o.id)                              AS order_count
            FROM orders o
            WHERE o.status = 'DELIVERED'
              AND EXTRACT(YEAR FROM o.created_at) = :year
            GROUP BY EXTRACT(MONTH FROM o.created_at)
            ORDER BY EXTRACT(MONTH FROM o.created_at)
            """, nativeQuery = true)
    List<Object[]> getMonthlyRevenue(@Param("year") int year);

    /** Doanh thu theo quý trong năm — EXTRACT() native PostgreSQL */
    @Query(value = """
            SELECT EXTRACT(QUARTER FROM o.created_at)::int  AS quarter,
                   COALESCE(SUM(o.total_amount), 0)          AS revenue,
                   COUNT(o.id)                               AS order_count
            FROM orders o
            WHERE o.status = 'DELIVERED'
              AND EXTRACT(YEAR FROM o.created_at) = :year
            GROUP BY EXTRACT(QUARTER FROM o.created_at)
            ORDER BY EXTRACT(QUARTER FROM o.created_at)
            """, nativeQuery = true)
    List<Object[]> getQuarterlyRevenue(@Param("year") int year);

    // ── Orders ───────────────────────────────────────────────────────────────

    /** Tổng số đơn trong khoảng thời gian */
    @Query(value = """
            SELECT COUNT(*)
            FROM orders
            WHERE created_at >= :from
              AND created_at <= :to
            """, nativeQuery = true)
    long countOrders(@Param("from") Instant from,
                     @Param("to")   Instant to);

    /** Số đơn hàng theo trạng thái */
    @Query(value = "SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status",
           nativeQuery = true)
    List<Object[]> countByStatus();

    // ── Users ────────────────────────────────────────────────────────────────

    /** Tổng user + số active + số bị khóa */
    @Query(value = """
            SELECT COUNT(*),
                   COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN status = 'LOCKED' THEN 1 ELSE 0 END), 0)
            FROM users
            """, nativeQuery = true)
    Object[] getUserStats();

    /** Users đăng ký mới trong khoảng thời gian */
    @Query(value = """
            SELECT COUNT(*)
            FROM users
            WHERE created_at >= :from
              AND created_at <= :to
            """, nativeQuery = true)
    long countNewUsers(@Param("from") Instant from,
                       @Param("to")   Instant to);

    // ── Products ─────────────────────────────────────────────────────────────

    /** Top N sản phẩm bán chạy nhất */
    @Query(value = """
            SELECT oi.product_id::text,
                   p.name,
                   p.thumbnail_url,
                   SUM(oi.quantity)    AS total_sold,
                   SUM(oi.total_price) AS total_revenue
            FROM order_items oi
            JOIN products p ON p.id = oi.product_id
            JOIN orders   o ON o.id = oi.order_id
            WHERE o.status = 'DELIVERED'
            GROUP BY oi.product_id, p.name, p.thumbnail_url
            ORDER BY total_sold DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopProducts(@Param("limit") int limit);
}
