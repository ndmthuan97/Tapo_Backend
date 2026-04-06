package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.statistics.DashboardStatsDto;
import backend.service.impl.StatisticsServiceImpl;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Statistics", description = "Thống kê & báo cáo doanh thu")
public class StatisticsController {

    private final StatisticsServiceImpl statisticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getDashboard(
            @RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        DashboardStatsDto stats = statisticsService.getDashboard(resolvedYear);
        return ResponseEntity.ok(ApiResponse.success("Dashboard statistics retrieved", stats));
    }

    /** Export revenue report for a year to Excel (.xlsx). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        byte[] xlsx = statisticsService.exportToExcel(resolvedYear);
        String filename = "tapo-revenue-" + resolvedYear + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentLength(xlsx.length);
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
