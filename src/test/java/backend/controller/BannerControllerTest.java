package backend.controller;

import backend.dto.banner.BannerDto;
import backend.exception.GlobalExceptionHandler;
import backend.model.entity.Banner;
import backend.repository.BannerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BannerController Unit Tests — standaloneSetup (mocks BannerRepository directly).
 * Covers: BANNER-001 ~ BANNER-004
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BannerController — Unit Tests")
class BannerControllerTest {

    @Mock BannerRepository bannerRepo;

    @InjectMocks BannerController bannerController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(bannerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Banner stubBanner(boolean active) {
        Banner b = new Banner();
        b.setId(UUID.randomUUID());
        b.setTitle("Test Banner");
        b.setImageUrl("https://cdn.tapo.vn/banner.jpg");
        b.setLinkUrl("https://tapo.vn/sale");
        b.setPosition(1);
        b.setIsActive(active);
        return b;
    }

    // ── GET /api/banners ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/banners")
    class GetActiveBanners {

        @Test
        @DisplayName("BANNER-001: xem banners active → 200, chỉ isActive=true, theo position")
        void getActiveBanners_200() throws Exception {
            Banner b1 = stubBanner(true);
            b1.setPosition(1);
            Banner b2 = stubBanner(true);
            b2.setPosition(2);
            given(bannerRepo.findAllByIsActiveTrueOrderByPositionAsc()).willReturn(List.of(b1, b2));

            mockMvc.perform(get("/api/banners"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].isActive").value(true));
        }

        @Test
        @DisplayName("BANNER-001b: không có banner active → 200, list rỗng")
        void getActiveBanners_empty_200() throws Exception {
            given(bannerRepo.findAllByIsActiveTrueOrderByPositionAsc()).willReturn(List.of());

            mockMvc.perform(get("/api/banners"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── GET /api/banners/admin ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/banners/admin")
    class GetAllBanners {

        @Test
        @DisplayName("BANNER-002: Admin xem tất cả banner → 200, bao gồm inactive")
        void getAllBanners_200() throws Exception {
            Banner active = stubBanner(true);
            Banner inactive = stubBanner(false);
            given(bannerRepo.findAllByOrderByPositionAsc()).willReturn(List.of(active, inactive));

            mockMvc.perform(get("/api/banners/admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ── PATCH /api/banners/{id}/active ────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/banners/{id}/active")
    class ToggleActive {

        @Test
        @DisplayName("BANNER-003: toggle active/inactive → 200, isActive đảo chiều")
        void toggleActive_200() throws Exception {
            UUID id = UUID.randomUUID();
            Banner b = stubBanner(true);  // currently active
            b.setId(id);
            given(bannerRepo.findById(id)).willReturn(Optional.of(b));
            given(bannerRepo.save(any(Banner.class))).willAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(patch("/api/banners/{id}/active", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isActive\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isActive").value(false));
        }
    }

    // ── POST /api/banners ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/banners")
    class CreateBanner {

        @Test
        @DisplayName("BANNER-004: tạo banner → 200, banner được lưu")
        void createBanner_200() throws Exception {
            Banner saved = stubBanner(true);
            given(bannerRepo.save(any(Banner.class))).willReturn(saved);

            String body = """
                    {
                      "title": "Summer Sale",
                      "imageUrl": "https://cdn.tapo.vn/summer.jpg",
                      "linkUrl": "https://tapo.vn/summer",
                      "position": 1,
                      "isActive": true
                    }
                    """;

            mockMvc.perform(post("/api/banners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Banner created"));
        }
    }
}
