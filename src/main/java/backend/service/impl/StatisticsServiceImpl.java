package backend.service.impl;

import backend.dto.statistics.DashboardStatsDto;
import backend.dto.statistics.DashboardStatsDto.RevenueDataPoint;
import backend.dto.statistics.DashboardStatsDto.TopProductDto;
import backend.repository.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl {

    private final StatisticsRepository statsRepo;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboard(int year) {
        // ── Time windows ────────────────────────────────────────────────────────
        int currentYear  = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        LocalDateTime thisMonthStart = LocalDate.of(currentYear, currentMonth, 1).atStartOfDay();
        LocalDateTime thisMonthEnd   = thisMonthStart.plusMonths(1).minusSeconds(1);

        LocalDateTime prevMonthStart = thisMonthStart.minusMonths(1);
        LocalDateTime prevMonthEnd   = thisMonthStart.minusSeconds(1);

        // ── Revenue ─────────────────────────────────────────────────────────────
        BigDecimal revenueThisMonth = statsRepo.getTotalRevenue(thisMonthStart, thisMonthEnd);
        BigDecimal revenuePrevMonth = statsRepo.getTotalRevenue(prevMonthStart, prevMonthEnd);
        BigDecimal totalRevenue     = statsRepo.getTotalRevenue(
                LocalDate.of(year, 1, 1).atStartOfDay(),
                LocalDate.of(year, 12, 31).atTime(LocalTime.MAX));

        double revenueGrowthPct = calcGrowth(revenueThisMonth, revenuePrevMonth);

        // ── Orders ───────────────────────────────────────────────────────────────
        long ordersThisMonth = statsRepo.countOrders(thisMonthStart, thisMonthEnd);
        long ordersPrevMonth = statsRepo.countOrders(prevMonthStart, prevMonthEnd);
        double ordersGrowthPct = ordersThisMonth > 0 && ordersPrevMonth > 0
                ? ((double)(ordersThisMonth - ordersPrevMonth) / ordersPrevMonth) * 100
                : 0;

        // ── Order status ─────────────────────────────────────────────────────────
        Map<String, Long> statusMap = statsRepo.countByStatus().stream()
                .collect(Collectors.toMap(r -> r[0].toString(), r -> (Long) r[1]));

        long totalOrders      = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long pendingOrders    = statusMap.getOrDefault("PENDING",    0L);
        long processingOrders = statusMap.getOrDefault("PROCESSING", 0L) +
                                statusMap.getOrDefault("SHIPPED",    0L);
        long deliveredOrders  = statusMap.getOrDefault("DELIVERED",  0L);
        long cancelledOrders  = statusMap.getOrDefault("CANCELLED",  0L);

        // ── Users ────────────────────────────────────────────────────────────────
        Object[] userStats = statsRepo.getUserStats();
        long totalUsers       = ((Number) userStats[0]).longValue();
        long activeUsers      = ((Number) userStats[1]).longValue();
        long lockedUsers      = ((Number) userStats[2]).longValue();
        long newUsersThisMonth = statsRepo.countNewUsers(thisMonthStart, thisMonthEnd);

        // ── Monthly chart ────────────────────────────────────────────────────────
        Map<Integer, Object[]> monthlyMap = statsRepo.getMonthlyRevenue(year).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).intValue(), r -> r));

        List<RevenueDataPoint> monthlyRevenue = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Object[] row = monthlyMap.get(m);
            monthlyRevenue.add(RevenueDataPoint.builder()
                    .label("T" + m)
                    .revenue(row != null ? (BigDecimal) row[1] : BigDecimal.ZERO)
                    .orderCount(row != null ? ((Number) row[2]).longValue() : 0L)
                    .build());
        }

        // ── Quarterly chart ──────────────────────────────────────────────────────
        Map<Integer, Object[]> quarterlyMap = statsRepo.getQuarterlyRevenue(year).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).intValue(), r -> r));

        List<RevenueDataPoint> quarterlyRevenue = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            Object[] row = quarterlyMap.get(q);
            quarterlyRevenue.add(RevenueDataPoint.builder()
                    .label("Q" + q)
                    .revenue(row != null ? (BigDecimal) row[1] : BigDecimal.ZERO)
                    .orderCount(row != null ? ((Number) row[2]).longValue() : 0L)
                    .build());
        }

        // ── Top products ─────────────────────────────────────────────────────────
        List<TopProductDto> topProducts = statsRepo.getTopProducts(5).stream()
                .map(r -> TopProductDto.builder()
                        .productId(r[0].toString())
                        .name(r[1].toString())
                        .thumbnailUrl(r[2] != null ? r[2].toString() : null)
                        .totalSold(((Number) r[3]).longValue())
                        .totalRevenue((BigDecimal) r[4])
                        .build())
                .collect(Collectors.toList());

        return DashboardStatsDto.builder()
                .totalRevenue(totalRevenue)
                .revenueThisMonth(revenueThisMonth)
                .revenuePrevMonth(revenuePrevMonth)
                .revenueGrowthPct(revenueGrowthPct)
                .totalOrders(totalOrders)
                .ordersThisMonth(ordersThisMonth)
                .ordersPrevMonth(ordersPrevMonth)
                .ordersGrowthPct(ordersGrowthPct)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .lockedUsers(lockedUsers)
                .newUsersThisMonth(newUsersThisMonth)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .monthlyRevenue(monthlyRevenue)
                .quarterlyRevenue(quarterlyRevenue)
                .topProducts(topProducts)
                .build();
    }

    private double calcGrowth(BigDecimal current, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) return 0;
        return current.subtract(prev)
                .divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
