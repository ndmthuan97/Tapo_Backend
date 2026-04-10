package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.flashsale.FlashSaleDto;
import backend.dto.flashsale.FlashSaleRequest;
import backend.model.enums.FlashSaleStatus;
import backend.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Flash Sales", description = "Flash sale management and public listing")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    // ── Public endpoints ────────────────────────────────────────────────────────

    @GetMapping("/api/flash-sales/active")
    @Operation(summary = "Public: danh sách flash sale đang ACTIVE (có countdown)")
    public ResponseEntity<ApiResponse<List<FlashSaleDto>>> getActiveSales() {
        return ResponseEntity.ok(ApiResponse.success(flashSaleService.getActiveSales()));
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────────

    @GetMapping("/api/admin/flash-sales")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: danh sách tất cả flash sale, filter theo status")
    public ResponseEntity<ApiResponse<List<FlashSaleDto>>> listFlashSales(
            @RequestParam(required = false) FlashSaleStatus status) {
        return ResponseEntity.ok(ApiResponse.success(flashSaleService.listFlashSales(status)));
    }

    @PostMapping("/api/admin/flash-sales")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: tạo flash sale mới")
    public ResponseEntity<ApiResponse<FlashSaleDto>> createFlashSale(
            @Valid @RequestBody FlashSaleRequest request) {
        FlashSaleDto created = flashSaleService.createFlashSale(request);
        return ResponseEntity.ok(ApiResponse.success("Flash sale đã được tạo", created));
    }

    @PutMapping("/api/admin/flash-sales/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: cập nhật flash sale (chỉ SCHEDULED)")
    public ResponseEntity<ApiResponse<FlashSaleDto>> updateFlashSale(
            @PathVariable UUID id,
            @Valid @RequestBody FlashSaleRequest request) {
        FlashSaleDto updated = flashSaleService.updateFlashSale(id, request);
        return ResponseEntity.ok(ApiResponse.success("Flash sale đã được cập nhật", updated));
    }

    @DeleteMapping("/api/admin/flash-sales/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: xóa flash sale (chỉ SCHEDULED)")
    public ResponseEntity<ApiResponse<Void>> deleteFlashSale(@PathVariable UUID id) {
        flashSaleService.deleteFlashSale(id);
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
