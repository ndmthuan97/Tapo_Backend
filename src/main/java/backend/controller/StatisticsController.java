package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.statistics.DashboardStatsDto;
import backend.service.impl.StatisticsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final StatisticsServiceImpl statisticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getDashboard(
            @RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        DashboardStatsDto stats = statisticsService.getDashboard(resolvedYear);
        return ResponseEntity.ok(ApiResponse.success("Dashboard statistics retrieved", stats));
    }
}
