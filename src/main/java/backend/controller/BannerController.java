package backend.controller;

import backend.dto.banner.BannerDto;
import backend.dto.banner.BannerRequest;
import backend.dto.common.ApiResponse;
import backend.model.entity.Banner;
import backend.repository.BannerRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Banners", description = "Quản lý banner trang chủ")
public class BannerController {

    private final BannerRepository bannerRepo;

    // ── Mapping helper ───────────────────────────────────────────────────────

    private BannerDto toDto(Banner b) {
        return new BannerDto(b.getId(), b.getTitle(), b.getImageUrl(),
                b.getLinkUrl(), b.getPosition(), b.getIsActive(), b.getCreatedAt());
    }

    // ── Public ───────────────────────────────────────────────────────────────

    /** GET /api/banners — danh sách banner đang hiển thị (cho trang chủ) */
    @GetMapping("/api/banners")
    public ResponseEntity<ApiResponse<List<BannerDto>>> getActiveBanners() {
        List<BannerDto> list = bannerRepo.findAllByIsActiveTrueOrderByPositionAsc()
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    /** GET /api/banners/admin — tất cả banner (kể cả ẩn) */
    @GetMapping("/api/banners/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BannerDto>>> getAllBanners() {
        List<BannerDto> list = bannerRepo.findAllByOrderByPositionAsc()
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /** POST /api/banners */
    @PostMapping("/api/banners")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BannerDto>> createBanner(@Valid @RequestBody BannerRequest req) {
        Banner banner = new Banner();
        applyRequest(banner, req);
        return ResponseEntity.ok(ApiResponse.success("Banner created", toDto(bannerRepo.save(banner))));
    }

    /** PUT /api/banners/{id} */
    @PutMapping("/api/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BannerDto>> updateBanner(
            @PathVariable UUID id, @Valid @RequestBody BannerRequest req) {
        Banner banner = bannerRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found: " + id));
        applyRequest(banner, req);
        return ResponseEntity.ok(ApiResponse.success("Banner updated", toDto(bannerRepo.save(banner))));
    }

    /** PATCH /api/banners/{id}/active — toggle hiển thị */
    @PatchMapping("/api/banners/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BannerDto>> toggleActive(
            @PathVariable UUID id, @RequestBody java.util.Map<String, Boolean> body) {
        Banner banner = bannerRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found: " + id));
        banner.setIsActive(body.getOrDefault("isActive", !banner.getIsActive()));
        return ResponseEntity.ok(ApiResponse.success("Banner updated", toDto(bannerRepo.save(banner))));
    }

    /** DELETE /api/banners/{id} */
    @DeleteMapping("/api/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable UUID id) {
        bannerRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Banner deleted", null));
    }

    // ── Private helper ───────────────────────────────────────────────────────

    private void applyRequest(Banner b, BannerRequest req) {
        b.setTitle(req.title());
        b.setImageUrl(req.imageUrl());
        b.setLinkUrl(req.linkUrl());
        b.setPosition(req.position() != null ? req.position() : 0);
        b.setIsActive(req.isActive() != null ? req.isActive() : true);
    }
}
