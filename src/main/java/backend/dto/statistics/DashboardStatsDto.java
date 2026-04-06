package backend.dto.statistics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardStatsDto {

    // ── KPI cards ────────────────────────────────────────────────────────────
    private BigDecimal totalRevenue;
    private BigDecimal revenueThisMonth;
    private BigDecimal revenuePrevMonth;
    private double revenueGrowthPct;

    private long totalOrders;
    private long ordersThisMonth;
    private long ordersPrevMonth;
    private double ordersGrowthPct;
    private BigDecimal avgOrderValue; // AOV = totalRevenue / totalOrders

    private long totalUsers;
    private long activeUsers;
    private long lockedUsers;
    private long newUsersThisMonth;
    private long returningUsers;

    // ── Order status breakdown ────────────────────────────────────────────────
    private long pendingOrders;
    private long processingOrders;
    private long deliveredOrders;
    private long cancelledOrders;

    // ── Chart data ────────────────────────────────────────────────────────────
    private List<RevenueDataPoint> monthlyRevenue;
    private List<RevenueDataPoint> quarterlyRevenue;

    // ── Top products ──────────────────────────────────────────────────────────
    private List<TopProductDto> topProducts;

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class RevenueDataPoint {
        private String label;        // "T1".."T12" or "Q1".."Q4"
        private BigDecimal revenue;
        private long orderCount;
    }

    @Data
    @Builder
    public static class TopProductDto {
        private String productId;
        private String name;
        private String thumbnailUrl;
        private long totalSold;
        private BigDecimal totalRevenue;
    }
}
