package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.voucher.CreateVoucherRequest;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.dto.voucher.ValidateVoucherResponse;
import backend.dto.voucher.VoucherDto;
import backend.service.impl.VoucherServiceImpl;
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
public class VoucherController {

    private final VoucherServiceImpl voucherService;

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
}
