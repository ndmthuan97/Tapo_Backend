package backend.service.impl;

import backend.dto.statistics.DashboardStatsDto;
import backend.dto.statistics.DashboardStatsDto.RevenueDataPoint;
import backend.dto.statistics.DashboardStatsDto.TopProductDto;
import backend.repository.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl {

    private final StatisticsRepository statsRepo;

    /** Null-safe BigDecimal coalescing — SQL SUM returns null if no rows match. */
    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    /**
     * Returns cached dashboard statistics for a given year.
     * Cache key = year — each year is cached independently.
     * TTL: 5 minutes (configured in RedisConfig).
     */
    @Cacheable(value = "dashboard", key = "#year")
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboard(int year) {
        // ── Time windows ────────────────────────────────────────────────────────
        int currentYear  = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        LocalDate thisMonthDate  = LocalDate.of(currentYear, currentMonth, 1);
        Instant thisMonthStart   = thisMonthDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant thisMonthEnd     = thisMonthDate.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
        Instant prevMonthStart   = thisMonthDate.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant prevMonthEnd     = thisMonthStart.minusSeconds(1);

        // ── Revenue ─────────────────────────────────────────────────────────────
        BigDecimal revenueThisMonth = nvl(statsRepo.getTotalRevenue(thisMonthStart, thisMonthEnd));
        BigDecimal revenuePrevMonth = nvl(statsRepo.getTotalRevenue(prevMonthStart, prevMonthEnd));
        BigDecimal totalRevenue     = nvl(statsRepo.getTotalRevenue(
                LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1)));

        double revenueGrowthPct = calcGrowth(revenueThisMonth, revenuePrevMonth);

        // ── Orders ───────────────────────────────────────────────────────────────
        long ordersThisMonth = statsRepo.countOrders(thisMonthStart, thisMonthEnd);
        long ordersPrevMonth = statsRepo.countOrders(prevMonthStart, prevMonthEnd);
        double ordersGrowthPct = ordersThisMonth > 0 && ordersPrevMonth > 0
                ? ((double)(ordersThisMonth - ordersPrevMonth) / ordersPrevMonth) * 100
                : 0;


        // ── Order status ─────────────────────────────────────────────────────────
        Map<String, Long> statusMap = statsRepo.countByStatus().stream()
                .collect(Collectors.toMap(
                        r -> r[0].toString(),
                        r -> ((Number) r[1]).longValue()));

        long totalOrders      = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long pendingOrders    = statusMap.getOrDefault("PENDING",    0L);
        long processingOrders = statusMap.getOrDefault("PROCESSING", 0L) +
                                statusMap.getOrDefault("SHIPPED",    0L);
        long deliveredOrders  = statusMap.getOrDefault("DELIVERED",  0L);
        long cancelledOrders  = statusMap.getOrDefault("CANCELLED",  0L);
        long returnOrders     = statsRepo.countReturnOrders();

        // ── AOV (Average Order Value) ─────────────────────────────────────────
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Users ────────────────────────────────────────────────────────────────
        List<Object[]> userStatsList = statsRepo.getUserStats();
        Object[] userStats = (userStatsList != null && !userStatsList.isEmpty()) ? userStatsList.get(0) : null;
        long totalUsers        = (userStats != null && userStats.length > 0) ? ((Number) userStats[0]).longValue() : 0L;
        long activeUsers       = (userStats != null && userStats.length > 1) ? ((Number) userStats[1]).longValue() : 0L;
        long lockedUsers       = (userStats != null && userStats.length > 2) ? ((Number) userStats[2]).longValue() : 0L;
        long newUsersThisMonth  = statsRepo.countNewUsers(thisMonthStart, thisMonthEnd);
        long returningUsers     = statsRepo.countReturningUsers();

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
                .avgOrderValue(avgOrderValue)
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .lockedUsers(lockedUsers)
                .newUsersThisMonth(newUsersThisMonth)
                .returningUsers(returningUsers)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .returnOrders(returnOrders)
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

    /**
     * Export revenue report for a given year as Excel (.xlsx) bytes.
     * Reuses the cached getDashboard() result to avoid extra DB queries.
     * Sheets: Monthly Revenue | Quarterly Revenue | Top Products | KPI Summary.
     */
    public byte[] exportToExcel(int year) {
        DashboardStatsDto stats = getDashboard(year);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Shared styles ───────────────────────────────────────────────
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle numberStyle = wb.createCellStyle();
            DataFormat df = wb.createDataFormat();
            numberStyle.setDataFormat(df.getFormat("#,##0"));

            // ── Sheet 1: Monthly Revenue ─────────────────────────────────────
            Sheet monthly = wb.createSheet("Monthly Revenue");
            writeTitle(monthly, titleStyle, "Doanh thu theo thang - Nam " + year, 3);
            writeRow(monthly, 1, headerStyle, "Thang", "Doanh thu (VND)", "So don hang");
            int r = 2;
            for (RevenueDataPoint dp : stats.getMonthlyRevenue()) {
                Row row = monthly.createRow(r++);
                row.createCell(0).setCellValue(dp.getLabel());
                Cell rev = row.createCell(1); rev.setCellValue(dp.getRevenue().doubleValue()); rev.setCellStyle(numberStyle);
                row.createCell(2).setCellValue(dp.getOrderCount());
            }
            autoSize(monthly, 3);

            // ── Sheet 2: Quarterly Revenue ───────────────────────────────────
            Sheet quarterly = wb.createSheet("Quarterly Revenue");
            writeTitle(quarterly, titleStyle, "Doanh thu theo quy - Nam " + year, 3);
            writeRow(quarterly, 1, headerStyle, "Quy", "Doanh thu (VND)", "So don hang");
            r = 2;
            for (RevenueDataPoint dp : stats.getQuarterlyRevenue()) {
                Row row = quarterly.createRow(r++);
                row.createCell(0).setCellValue(dp.getLabel());
                Cell rev = row.createCell(1); rev.setCellValue(dp.getRevenue().doubleValue()); rev.setCellStyle(numberStyle);
                row.createCell(2).setCellValue(dp.getOrderCount());
            }
            autoSize(quarterly, 3);

            // ── Sheet 3: Top Products ────────────────────────────────────────
            Sheet top = wb.createSheet("Top Products");
            writeTitle(top, titleStyle, "Top san pham ban chay", 4);
            writeRow(top, 1, headerStyle, "Hang", "Ten san pham", "So luong da ban", "Tong doanh thu (VND)");
            r = 2;
            int rank = 1;
            for (TopProductDto tp : stats.getTopProducts()) {
                Row row = top.createRow(r++);
                row.createCell(0).setCellValue(rank++);
                row.createCell(1).setCellValue(tp.getName());
                row.createCell(2).setCellValue(tp.getTotalSold());
                Cell rev = row.createCell(3); rev.setCellValue(tp.getTotalRevenue().doubleValue()); rev.setCellStyle(numberStyle);
            }
            autoSize(top, 4);

            // ── Sheet 4: KPI Summary ─────────────────────────────────────────
            Sheet kpi = wb.createSheet("KPI Summary");
            writeTitle(kpi, titleStyle, "Tong hop KPI - Nam " + year, 2);
            writeRow(kpi, 1, headerStyle, "Chi so", "Gia tri");
            Object[][] kpis = {
                { "Tong doanh thu (VND)",       stats.getTotalRevenue().doubleValue() },
                { "Doanh thu thang nay (VND)",  stats.getRevenueThisMonth().doubleValue() },
                { "Tang truong doanh thu (%)",   stats.getRevenueGrowthPct() },
                { "Tong don hang",               (double) stats.getTotalOrders() },
                { "Don hang thang nay",          (double) stats.getOrdersThisMonth() },
                { "Gia tri TB don hang (VND)",   stats.getAvgOrderValue().doubleValue() },
                { "Tong nguoi dung",             (double) stats.getTotalUsers() },
                { "Nguoi dung moi thang nay",    (double) stats.getNewUsersThisMonth() },
                { "Don cho xu ly",               (double) stats.getPendingOrders() },
                { "Don da giao",                 (double) stats.getDeliveredOrders() },
                { "Don bi huy",                  (double) stats.getCancelledOrders() },
            };
            r = 2;
            for (Object[] kv : kpis) {
                Row row = kpi.createRow(r++);
                row.createCell(0).setCellValue((String) kv[0]);
                Cell val = row.createCell(1); val.setCellValue((Double) kv[1]); val.setCellStyle(numberStyle);
            }
            autoSize(kpi, 2);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to generate Excel report", ex);
        }
    }

    // ── POI helpers ─────────────────────────────────────────────────────────

    private static void writeTitle(Sheet sheet, CellStyle style, String title, int colSpan) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        if (colSpan > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colSpan - 1));
        }
    }

    private static void writeRow(Sheet sheet, int rowIdx, CellStyle style, String... headers) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
            // add slight padding so text is not clipped
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1024, 15000));
        }
    }

    /**
     * Auto-evict dashboard cache o 00:05 moi ngay.
     * Dam bao data luon fresh khi qua ngay moi (monthly stats doi).
     */
    @CacheEvict(value = "dashboard", allEntries = true)
    @Scheduled(cron = "0 5 0 * * *")
    public void evictDashboardCacheDaily() {
        // Spring AOP handles the eviction — no body needed
    }
}
