package backend.controller;

import backend.dto.common.CustomCode;
import backend.dto.product.*;
import backend.exception.AppException;
import backend.exception.GlobalExceptionHandler;
import backend.model.enums.ProductStatus;
import backend.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: PROD-001 ~ PROD-027
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController — Unit Tests")
class ProductControllerTest {

    @Mock ProductService productService;

    @InjectMocks ProductController productController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductDto stubProductDto(UUID id, String name, ProductStatus status) {
        return new ProductDto(id, name, "slug-" + name.toLowerCase().replace(" ", "-"),
                "Description", BigDecimal.valueOf(500_000), null,
                10, status, "https://cdn.tapo.vn/img.jpg",
                Collections.emptyList(), BigDecimal.valueOf(4.5), 5, 100,
                UUID.randomUUID(), "Category", UUID.randomUUID(), "Brand",
                Collections.emptyMap(), null, null);
    }

    private ProductRequest validProductRequest() {
        return new ProductRequest(
                "Test Product", null, "Description",
                UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.valueOf(500_000), null, 10,
                "https://cdn.tapo.vn/img.jpg", Collections.emptyList(),
                Collections.emptyMap(), ProductStatus.DRAFT);
    }

    // ── GET /api/products ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products — list & filter")
    class GetProducts {

        @Test
        @DisplayName("PROD-001: lấy danh sách mặc định → 200, page 0, size 16")
        void getProducts_default_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Laptop", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(), any(), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].name").value("Laptop"));
        }

        @Test
        @DisplayName("PROD-002: lọc theo categoryId → 200")
        void getProducts_filterByCategory_200() throws Exception {
            UUID catId = UUID.randomUUID();
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Phone", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), eq(catId), any(),
                    any(), any(), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products").param("categoryId", catId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name").value("Phone"));
        }

        @Test
        @DisplayName("PROD-003: lọc theo brandId → 200")
        void getProducts_filterByBrand_200() throws Exception {
            UUID brandId = UUID.randomUUID();
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Dell", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), eq(brandId),
                    any(), any(), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products").param("brandId", brandId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name").value("Dell"));
        }

        @Test
        @DisplayName("PROD-004: lọc theo khoảng giá → 200")
        void getProducts_filterByPrice_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Budget Phone", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), any(),
                    eq(100_000L), eq(500_000L), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products")
                            .param("minPrice", "100000")
                            .param("maxPrice", "500000"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PROD-005: tìm kiếm theo tên → 200")
        void getProducts_searchByName_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Laptop Gaming", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), eq("laptop"), any(), any(), any(),
                    any(), any(), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products").param("search", "laptop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name").value("Laptop Gaming"));
        }

        @Test
        @DisplayName("PROD-006: lọc hàng còn → 200")
        void getProducts_filterInStock_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "InStock", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(), any(), any(), eq(true), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products").param("inStock", "true"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PROD-007: lọc theo minRating=4 → 200")
        void getProducts_filterByRating_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(List.of(stubProductDto(UUID.randomUUID(), "Good Product", ProductStatus.ACTIVE)));
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(), any(), eq(4.0), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products").param("minRating", "4"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PROD-008: sắp xếp theo giá tăng dần → 200")
        void getProducts_sortByPriceAsc_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(Collections.emptyList());
            given(productService.getProducts(anyInt(), anyInt(), any(), any(), any(), any(),
                    any(), any(), any(), any(), eq("price,asc"))).willReturn(page);

            mockMvc.perform(get("/api/products").param("sort", "price,asc"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PROD-009: phân trang page=2&size=10 → 200")
        void getProducts_pagination_200() throws Exception {
            Page<ProductDto> page = new PageImpl<>(Collections.emptyList());
            given(productService.getProducts(eq(2), eq(10), any(), any(), any(), any(),
                    any(), any(), any(), any(), anyString())).willReturn(page);

            mockMvc.perform(get("/api/products")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductById {

        @Test
        @DisplayName("PROD-010: xem chi tiết sản phẩm → 200, đầy đủ thông tin")
        void getProduct_found_200() throws Exception {
            UUID id = UUID.randomUUID();
            ProductDto dto = stubProductDto(id, "MacBook Pro", ProductStatus.ACTIVE);
            given(productService.getProduct(id)).willReturn(dto);

            mockMvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id.toString()))
                    .andExpect(jsonPath("$.data.name").value("MacBook Pro"));
        }

        @Test
        @DisplayName("PROD-011: sản phẩm không tồn tại → 404")
        void getProduct_notFound_404() throws Exception {
            UUID id = UUID.randomUUID();
            given(productService.getProduct(id)).willThrow(new AppException(CustomCode.PRODUCT_NOT_FOUND));

            mockMvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PROD-012: sản phẩm DRAFT → 404 khi truy cập public")
        void getProduct_draftNotVisiblePublic_404() throws Exception {
            UUID id = UUID.randomUUID();
            given(productService.getProduct(id)).willThrow(new AppException(CustomCode.PRODUCT_NOT_FOUND));

            mockMvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/products/suggest ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/suggest")
    class Suggest {

        @Test
        @DisplayName("PROD-013: gợi ý tìm kiếm → 200, danh sách gợi ý")
        void suggestProducts_200() throws Exception {
            List<SuggestDto> suggestions = List.of(
                    new SuggestDto(UUID.randomUUID(), "iPhone 15", "iphone-15",
                            "https://cdn.tapo.vn/iphone.jpg", BigDecimal.valueOf(25_000_000)),
                    new SuggestDto(UUID.randomUUID(), "iPhone 14", "iphone-14",
                            "https://cdn.tapo.vn/iphone14.jpg", BigDecimal.valueOf(20_000_000))
            );
            given(productService.suggestProducts("ip")).willReturn(suggestions);

            mockMvc.perform(get("/api/products/suggest").param("q", "ip"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].name").value("iPhone 15"));
        }

        @Test
        @DisplayName("PROD-013b: gợi ý không tìm thấy → 200, danh sách rỗng")
        void suggestProducts_noMatch_200() throws Exception {
            given(productService.suggestProducts("xyz123")).willReturn(Collections.emptyList());

            mockMvc.perform(get("/api/products/suggest").param("q", "xyz123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ── GET /api/products/{id}/related ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{id}/related")
    class RelatedProducts {

        @Test
        @DisplayName("PROD-014: sản phẩm liên quan → 200, tối đa 8 sản phẩm")
        void getRelatedProducts_200() throws Exception {
            UUID id = UUID.randomUUID();
            List<ProductDto> related = List.of(
                    stubProductDto(UUID.randomUUID(), "Related A", ProductStatus.ACTIVE),
                    stubProductDto(UUID.randomUUID(), "Related B", ProductStatus.ACTIVE)
            );
            given(productService.getRelatedProducts(id, 8)).willReturn(related);

            mockMvc.perform(get("/api/products/{id}/related", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ── GET /api/products/metadata ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/metadata")
    class Metadata {

        @Test
        @DisplayName("PROD-015: metadata categories + brands → 200")
        void getMetadata_200() throws Exception {
            given(productService.getAllCategories()).willReturn(List.of(
                    new SimpleRefDto(UUID.randomUUID(), "Laptop", "laptop")));
            given(productService.getAllBrands()).willReturn(List.of(
                    new SimpleRefDto(UUID.randomUUID(), "Apple", "apple")));

            mockMvc.perform(get("/api/products/metadata"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categories").isArray())
                    .andExpect(jsonPath("$.data.brands").isArray());
        }
    }

    // ── POST /api/products ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/products — admin create")
    class CreateProduct {

        @Test
        @DisplayName("PROD-020: tạo sản phẩm mới → 201, status=DRAFT mặc định")
        void createProduct_valid_201() throws Exception {
            ProductRequest req = validProductRequest();
            UUID newId = UUID.randomUUID();
            ProductDto created = stubProductDto(newId, "Test Product", ProductStatus.DRAFT);
            given(productService.createProduct(any(ProductRequest.class))).willReturn(created);

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        }

        @Test
        @DisplayName("PROD-021: tạo sản phẩm thiếu tên → 400 validation error")
        void createProduct_missingName_400() throws Exception {
            String body = """
                    {
                      "categoryId": "%s",
                      "brandId": "%s",
                      "price": 500000,
                      "stock": 10
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PROD-021b: tạo sản phẩm thiếu price → 400 validation error")
        void createProduct_missingPrice_400() throws Exception {
            String body = """
                    {
                      "name": "Test Product",
                      "categoryId": "%s",
                      "brandId": "%s",
                      "stock": 10
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/products/{id} — admin update")
    class UpdateProduct {

        @Test
        @DisplayName("PROD-022: cập nhật sản phẩm → 200, đúng dữ liệu mới")
        void updateProduct_valid_200() throws Exception {
            UUID id = UUID.randomUUID();
            ProductRequest req = validProductRequest();
            ProductDto updated = stubProductDto(id, "Updated Product", ProductStatus.ACTIVE);
            given(productService.updateProduct(eq(id), any(ProductRequest.class))).willReturn(updated);

            mockMvc.perform(put("/api/products/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Updated Product"));
        }
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/products/{id} — admin delete")
    class DeleteProduct {

        @Test
        @DisplayName("PROD-023: xoá sản phẩm → 200")
        void deleteProduct_200() throws Exception {
            UUID id = UUID.randomUUID();
            willDoNothing().given(productService).deleteProduct(id);

            mockMvc.perform(delete("/api/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Product deleted"));
        }
    }

    // ── DELETE /api/products/bulk ─────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/products/bulk — admin bulk delete")
    class BulkDelete {

        @Test
        @DisplayName("PROD-024: bulk delete → 200, xoá đúng các ID")
        void bulkDelete_200() throws Exception {
            Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
            willDoNothing().given(productService).bulkDelete(anySet());

            mockMvc.perform(delete("/api/products/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ids)))
                    .andExpect(status().isOk());
        }
    }

    // ── PATCH /api/products/bulk-status ──────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/products/bulk-status — bulk status update")
    class BulkStatus {

        @Test
        @DisplayName("PROD-025: bulk update status → 200, các sản phẩm chuyển sang ACTIVE")
        void bulkUpdateStatus_200() throws Exception {
            Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
            willDoNothing().given(productService).bulkUpdateStatus(anySet(), any(ProductStatus.class));

            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids.stream().map(UUID::toString).toList());
            body.put("status", "ACTIVE");

            mockMvc.perform(patch("/api/products/bulk-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }
    }
}
