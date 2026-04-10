package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.inventory.CreateReceiptRequest;
import backend.dto.inventory.InventoryReceiptDto;
import backend.model.enums.ReceiptType;
import backend.security.CustomUserDetails;
import backend.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock receipt management (IMPORT / EXPORT)")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Danh sách phiếu kho (có phân trang, filter theo type)")
    public ResponseEntity<ApiResponse<Page<InventoryReceiptDto>>> listReceipts(
            @RequestParam(required = false) ReceiptType type,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.listReceipts(type, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu kho theo ID")
    public ResponseEntity<ApiResponse<InventoryReceiptDto>> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getReceipt(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo phiếu kho IMPORT hoặc EXPORT — cập nhật stock ngay lập tức")
    public ResponseEntity<ApiResponse<InventoryReceiptDto>> createReceipt(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateReceiptRequest request) {
        InventoryReceiptDto created = inventoryService.createReceipt(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Phiếu kho đã được tạo", created));
    }
}
