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

@Repository
public interface StatisticsRepository extends JpaRepository<Order, UUID> {

    // ── Revenue ──────────────────────────────────────────────────────────────

    /** Tổng doanh thu các đơn DELIVERED trong khoảng thời gian */
    @Query("""
        SELECT COALESCE(SUM(o.totalAmount), 0)
        FROM Order o
        WHERE o.status = 'DELIVERED'
          AND o.createdAt BETWEEN :from AND :to
        """)
    BigDecimal getTotalRevenue(@Param("from") Instant from,
                               @Param("to")   Instant to);

    /** Doanh thu theo tháng trong năm — native SQL để dùng EXTRACT() của PostgreSQL */
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

    /** Doanh thu theo quý trong năm — native SQL để dùng EXTRACT() của PostgreSQL */
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
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to")
    long countOrders(@Param("from") Instant from,
                     @Param("to")   Instant to);

    /** Số đơn hàng theo trạng thái */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    // ── Users ────────────────────────────────────────────────────────────────

    /** Tổng user + số active + số bị khóa */
    @Query("SELECT COUNT(u), SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END), SUM(CASE WHEN u.status = 'LOCKED' THEN 1 ELSE 0 END) FROM backend.model.entity.User u")
    Object[] getUserStats();

    /** Users đăng ký mới trong khoảng thời gian */
    @Query("SELECT COUNT(u) FROM backend.model.entity.User u WHERE u.createdAt BETWEEN :from AND :to")
    long countNewUsers(@Param("from") Instant from,
                       @Param("to")   Instant to);

    // ── Products ─────────────────────────────────────────────────────────────

    /** Top N sản phẩm bán chạy nhất — native SQL để tránh LIMIT trong JPQL */
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
