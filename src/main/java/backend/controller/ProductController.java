package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.product.ProductDto;
import backend.dto.product.ProductRequest;
import backend.dto.product.SimpleRefDto;
import backend.model.enums.ProductStatus;
import backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── Public / authenticated ──────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductDto>>> getProducts(
            @RequestParam(defaultValue = "0")             int page,
            @RequestParam(defaultValue = "16")            int size,
            @RequestParam(required = false)               String search,
            @RequestParam(required = false)               ProductStatus status,
            @RequestParam(required = false)               UUID categoryId,
            @RequestParam(required = false)               UUID brandId,
            @RequestParam(required = false)               Long minPrice,
            @RequestParam(required = false)               Long maxPrice,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                productService.getProducts(page, size, search, status, categoryId, brandId, minPrice, maxPrice, sort)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(id)));
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getRelatedProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(productService.getRelatedProducts(id, limit)));
    }

    /** Dropdown data for the create/edit form */
    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<Map<String, List<SimpleRefDto>>>> getMetadata() {
        Map<String, List<SimpleRefDto>> data = Map.of(
                "categories", productService.getAllCategories(),
                "brands",     productService.getAllBrands()
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── Admin / Sales only ──────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted", null));
    }
}
