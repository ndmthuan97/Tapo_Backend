package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.returnrequest.CreateReturnRequest;
import backend.dto.returnrequest.ReturnRequestDto;
import backend.model.enums.ReturnRequestStatus;
import backend.security.CustomUserDetails;
import backend.service.impl.ReturnRequestServiceImpl;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Return Request", description = "Quản lý yêu cầu đổi/trả hàng")
public class ReturnRequestController {

        private final ReturnRequestServiceImpl returnService;

        // ── Customer ──────────────────────────────────────────────────────────────

        /**
         * POST /api/orders/{orderId}/return
         * Submit a return request for a delivered order.
         */
        @PostMapping("/api/orders/{orderId}/return")
        public ResponseEntity<ApiResponse<ReturnRequestDto>> createReturn(
                        @AuthenticationPrincipal CustomUserDetails principal,
                        @PathVariable UUID orderId,
                        @Valid @RequestBody CreateReturnRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                "Yêu cầu hoàn trả đã được gửi. Chúng tôi sẽ xử lý trong 3–5 ngày làm việc.",
                                returnService.createReturn(principal.getId(), orderId, request)));
        }

        /**
         * GET /api/orders/{orderId}/return
         * Get current return request for an order.
         */
        @GetMapping("/api/orders/{orderId}/return")
        public ResponseEntity<ApiResponse<ReturnRequestDto>> getByOrder(
                        @AuthenticationPrincipal CustomUserDetails principal,
                        @PathVariable UUID orderId) {
                return ResponseEntity.ok(ApiResponse.success(
                                returnService.getByOrder(principal.getId(), orderId)));
        }

        /**
         * GET /api/orders/returns — list all my returns
         */
        @GetMapping("/api/orders/returns")
        public ResponseEntity<ApiResponse<Page<ReturnRequestDto>>> getMyReturns(
                        @AuthenticationPrincipal CustomUserDetails principal,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return ResponseEntity.ok(ApiResponse.success(
                                returnService.getMyReturns(principal.getId(),
                                                PageRequest.of(page, size, Sort.by("createdAt").descending()))));
        }

        // ── Admin ─────────────────────────────────────────────────────────────────

        /**
         * GET /api/admin/returns?status=PENDING&page=0&size=20
         */
        @GetMapping("/api/admin/returns")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Page<ReturnRequestDto>>> listAll(
                        @RequestParam(required = false) ReturnRequestStatus status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                return ResponseEntity.ok(ApiResponse.success(
                                returnService.listAll(status,
                                                PageRequest.of(page, size, Sort.by("createdAt").descending()))));
        }

        /**
         * PATCH /api/admin/returns/{id}/status?status=APPROVED&note=...
         */
        @PatchMapping("/api/admin/returns/{id}/status")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<ReturnRequestDto>> updateStatus(
                        @AuthenticationPrincipal CustomUserDetails principal,
                        @PathVariable UUID id,
                        @RequestParam ReturnRequestStatus status,
                        @RequestParam(required = false, defaultValue = "") String note) {
                return ResponseEntity.ok(ApiResponse.success(
                                "Cập nhật trạng thái thành công",
                                returnService.updateStatus(principal.getId(), id, status, note)));
        }
}
