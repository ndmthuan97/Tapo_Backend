package backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import backend.model.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    BigDecimal getTotalRevenue(@Param("from") LocalDateTime from,
                               @Param("to")   LocalDateTime to);

    /** Doanh thu theo tháng trong năm */
    @Query("""
        SELECT FUNCTION('MONTH', o.createdAt) as month,
               COALESCE(SUM(o.totalAmount), 0) as revenue,
               COUNT(o.id) as orderCount
        FROM Order o
        WHERE o.status = 'DELIVERED'
          AND FUNCTION('YEAR', o.createdAt) = :year
        GROUP BY FUNCTION('MONTH', o.createdAt)
        ORDER BY FUNCTION('MONTH', o.createdAt)
        """)
    List<Object[]> getMonthlyRevenue(@Param("year") int year);

    /** Doanh thu theo quý trong năm */
    @Query("""
        SELECT FUNCTION('QUARTER', o.createdAt) as quarter,
               COALESCE(SUM(o.totalAmount), 0) as revenue,
               COUNT(o.id) as orderCount
        FROM Order o
        WHERE o.status = 'DELIVERED'
          AND FUNCTION('YEAR', o.createdAt) = :year
        GROUP BY FUNCTION('QUARTER', o.createdAt)
        ORDER BY FUNCTION('QUARTER', o.createdAt)
        """)
    List<Object[]> getQuarterlyRevenue(@Param("year") int year);

    // ── Orders ───────────────────────────────────────────────────────────────

    /** Tổng số đơn trong khoảng thời gian */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to")
    long countOrders(@Param("from") LocalDateTime from,
                     @Param("to")   LocalDateTime to);

    /** Số đơn hàng theo trạng thái */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    // ── Users ────────────────────────────────────────────────────────────────

    /** Tổng user + số active + số bị khóa */
    @Query("SELECT COUNT(u), SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END), SUM(CASE WHEN u.status = 'LOCKED' THEN 1 ELSE 0 END) FROM backend.model.entity.User u")
    Object[] getUserStats();

    /** Users đăng ký mới trong khoảng thời gian */
    @Query("SELECT COUNT(u) FROM backend.model.entity.User u WHERE u.createdAt BETWEEN :from AND :to")
    long countNewUsers(@Param("from") LocalDateTime from,
                       @Param("to")   LocalDateTime to);

    // ── Products ─────────────────────────────────────────────────────────────

    /** Top N sản phẩm bán chạy nhất */
    @Query("""
        SELECT oi.product.id, oi.product.name, oi.product.thumbnailUrl,
               SUM(oi.quantity) as totalSold,
               SUM(oi.totalPrice) as totalRevenue
        FROM OrderItem oi
        WHERE oi.order.status = 'DELIVERED'
        GROUP BY oi.product.id, oi.product.name, oi.product.thumbnailUrl
        ORDER BY totalSold DESC
        LIMIT :limit
        """)
    List<Object[]> getTopProducts(@Param("limit") int limit);
}
