package backend.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
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
 * Extends the minimal Repository marker (not JpaRepository) to avoid
 * ambiguous-bean conflict with OrderRepository for the same entity.
 */
@org.springframework.stereotype.Repository
public interface StatisticsRepository extends Repository<Order, UUID> {

    // ── Revenue ──────────────────────────────────────────────────────────────

    /** Tổng doanh thu các đơn DELIVERED trong khoảng thời gian */
    @Query(value = """
            SELECT COALESCE(SUM(o.total_amount), 0::numeric)
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
                   COALESCE(SUM(o.total_amount), 0::numeric) AS revenue,
                   COUNT(o.id)                               AS order_count
            FROM orders o
            WHERE o.status = 'DELIVERED'
              AND EXTRACT(YEAR FROM o.created_at) = :year
            GROUP BY EXTRACT(MONTH FROM o.created_at)
            ORDER BY EXTRACT(MONTH FROM o.created_at)
            """, nativeQuery = true)
    List<Object[]> getMonthlyRevenue(@Param("year") int year);

    /** Doanh thu theo quý trong năm — EXTRACT() native PostgreSQL */
    @Query(value = """
            SELECT EXTRACT(QUARTER FROM o.created_at)::int   AS quarter,
                   COALESCE(SUM(o.total_amount), 0::numeric)  AS revenue,
                   COUNT(o.id)                                AS order_count
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
    List<Object[]> getUserStats();

    /** Users đăng ký mới trong khoảng thời gian */
    @Query(value = """
            SELECT COUNT(*)
            FROM users
            WHERE created_at >= :from
              AND created_at <= :to
            """, nativeQuery = true)
    long countNewUsers(@Param("from") Instant from,
                       @Param("to")   Instant to);

    /** Users quay lại — có từ 2 đơn hợp lệ (trừ CANCELLED/RETURNED) trở lên */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT o.user_id
                FROM orders o
                WHERE o.status NOT IN ('CANCELLED', 'RETURNED')
                GROUP BY o.user_id
                HAVING COUNT(*) >= 2
            ) sub
            """, nativeQuery = true)
    long countReturningUsers();

    /** Users quay lại trong tuần — đăng nhập tuần này VÀ đã đăng ký trước tuần này */
    @Query(value = """
            SELECT COUNT(*)
            FROM users u
            WHERE u.last_login_at >= :weekStart
              AND u.last_login_at <  :weekEnd
              AND u.created_at    <  :weekStart
            """, nativeQuery = true)
    long countReturningUsersThisWeek(@Param("weekStart") Instant weekStart,
                                     @Param("weekEnd")   Instant weekEnd);

    /** Tổng số yêu cầu đổi/trả (tất cả status) */
    @Query(value = "SELECT COUNT(*) FROM return_requests", nativeQuery = true)
    long countReturnOrders();

    // ── Products ─────────────────────────────────────────────────────────────

    /** Top N sản phẩm bán chạy nhất (trừ CANCELLED và RETURNED) */
    @Query(value = """
            SELECT oi.product_id::text,
                   p.name,
                   p.thumbnail_url,
                   SUM(oi.quantity)    AS total_sold,
                   SUM(oi.total_price) AS total_revenue
            FROM order_items oi
            JOIN products p ON p.id = oi.product_id
            JOIN orders   o ON o.id = oi.order_id
            WHERE o.status NOT IN ('CANCELLED', 'RETURNED')
            GROUP BY oi.product_id, p.name, p.thumbnail_url
            ORDER BY total_sold DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopProducts(@Param("limit") int limit);

    /** Thống kê sản phẩm theo trạng thái: total, active, inactive, draft */
    @Query(value = """
            SELECT COUNT(*),
                   COALESCE(SUM(CASE WHEN status = 'ACTIVE'   THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN status = 'INACTIVE' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN status = 'DRAFT'    THEN 1 ELSE 0 END), 0)
            FROM products
            """, nativeQuery = true)
    List<Object[]> getProductStats();
}
