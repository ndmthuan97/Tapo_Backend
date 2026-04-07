package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.voucher.CreateVoucherRequest;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.dto.voucher.ValidateVoucherResponse;
import backend.dto.voucher.VoucherDto;
import backend.service.VoucherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Voucher", description = "Quản lý mã giảm giá")
public class VoucherController {

    private final VoucherService voucherService;

    // ── Customer: browse active vouchers ──────────────────────────────────────

    /**
     * GET /api/vouchers/active
     * Public — no auth required.
     * Returns paginated list of ACTIVE vouchers sorted by endDate ASC
     * (sắp hết hạn trước) so users can act quickly.
     */
    @GetMapping("/api/vouchers/active")
    public ResponseEntity<ApiResponse<Page<VoucherDto>>> getActiveVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.listActive(PageRequest.of(page, Math.min(size, 24),
                        Sort.by("endDate").ascending()))
        ));
    }

    // ── Customer: validate a voucher code ─────────────────────────────────────

    /**
     * POST /api/vouchers/validate
     * Body: { "code": "SUMMER20", "subtotal": 500000 }
     * Returns: { discountAmount, voucher }
     */
    @PostMapping("/api/vouchers/validate")
    public ResponseEntity<ApiResponse<ValidateVoucherResponse>> validate(
            @Valid @RequestBody ValidateVoucherRequest request
    ) {
        ValidateVoucherResponse res = voucherService.validate(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Mã giảm giá hợp lệ! Bạn được giảm " + res.discountAmount() + " đ",
                res
        ));
    }

    // ── Admin: CRUD  ──────────────────────────────────────────────────────────

    @GetMapping("/api/admin/vouchers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<VoucherDto>>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.listAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
        ));
    }

    @PostMapping("/api/admin/vouchers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDto>> create(
            @Valid @RequestBody CreateVoucherRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Tạo voucher thành công", voucherService.create(request)));
    }

    @PutMapping("/api/admin/vouchers/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDto>> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", voucherService.toggleStatus(id)));
    }

    @PutMapping("/api/admin/vouchers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVoucherRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật voucher thành công", voucherService.update(id, request)));
    }

    @DeleteMapping("/api/admin/vouchers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        voucherService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa voucher", null));
    }
}
