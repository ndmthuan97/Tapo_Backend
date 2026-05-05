package backend.controller;

import backend.dto.statistics.DashboardStatsDto;
import backend.exception.GlobalExceptionHandler;
import backend.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatisticsController Unit Tests — standaloneSetup.
 * Covers: STAT-001 ~ STAT-003, STAT-005
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsController — Unit Tests")
class StatisticsControllerTest {

    @Mock StatisticsServiceImpl statisticsService;

    @InjectMocks StatisticsController statisticsController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(statisticsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DashboardStatsDto stubStats(int year) {
        return DashboardStatsDto.builder()
                .totalRevenue(BigDecimal.valueOf(500_000_000))
                .totalOrders(1_250L)
                .totalUsers(300L)
                .monthlyRevenue(Collections.emptyList())
                .quarterlyRevenue(Collections.emptyList())
                .topProducts(Collections.emptyList())
                .build();
    }

    // ── GET /api/admin/statistics/dashboard ───────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/statistics/dashboard")
    class Dashboard {

        @Test
        @DisplayName("STAT-001: dashboard stats năm hiện tại → 200, totalRevenue, totalOrders, totalCustomers")
        void getDashboard_currentYear_200() throws Exception {
            int currentYear = LocalDate.now().getYear();
            given(statisticsService.getDashboard(currentYear)).willReturn(stubStats(currentYear));

            mockMvc.perform(get("/api/admin/statistics/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRevenue").isNumber())
                    .andExpect(jsonPath("$.data.totalOrders").isNumber())
                    .andExpect(jsonPath("$.data.totalUsers").isNumber());
        }

        @Test
        @DisplayName("STAT-002: dashboard stats năm cụ thể (year=2024) → 200")
        void getDashboard_specificYear_200() throws Exception {
            given(statisticsService.getDashboard(2024)).willReturn(stubStats(2024));

            mockMvc.perform(get("/api/admin/statistics/dashboard").param("year", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRevenue").isNumber());
        }

        @Test
        @DisplayName("STAT-003: dashboard stats year=0 → dùng năm hiện tại → 200")
        void getDashboard_yearZero_usesCurrentYear_200() throws Exception {
            int currentYear = LocalDate.now().getYear();
            given(statisticsService.getDashboard(currentYear)).willReturn(stubStats(currentYear));

            mockMvc.perform(get("/api/admin/statistics/dashboard").param("year", "0"))
                    .andExpect(status().isOk());

            // year=0 resolves to currentYear
            then(statisticsService).should().getDashboard(currentYear);
        }
    }

    // ── STAT-005: CUSTOMER không xem được statistics ───────────────────────────

    @Nested
    @DisplayName("STAT-005 — @PreAuthorize ADMIN check")
    class Authorization {

        @Test
        @DisplayName("STAT-005: StatisticsController class-level @PreAuthorize('hasRole(ADMIN)')")
        void statisticsController_hasClassLevelPreAuthorize() {
            PreAuthorize annotation = StatisticsController.class.getAnnotation(PreAuthorize.class);

            assertThat(annotation).as("@PreAuthorize must be present on StatisticsController").isNotNull();
            assertThat(annotation.value()).contains("ADMIN");
        }
    }
}
