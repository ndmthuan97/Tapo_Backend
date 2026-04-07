package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.product.ProductRequest;
import backend.exception.AppException;
import backend.model.entity.Brand;
import backend.model.entity.Category;
import backend.model.entity.Product;
import backend.model.enums.ProductStatus;
import backend.repository.BrandRepository;
import backend.repository.CategoryRepository;
import backend.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl — Unit Tests")
class ProductServiceImplTest {

    @Mock ProductRepository  productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BrandRepository    brandRepository;

    @InjectMocks ProductServiceImpl productService;

    // ── getProduct ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProduct: product exists → returns DTO")
    void getProduct_found_returnsDto() {
        UUID      id      = UUID.randomUUID();
        Product   product = stubProduct(id);
        given(productRepository.findByIdAndDeletedFalse(id)).willReturn(Optional.of(product));

        var dto = productService.getProduct(id);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Laptop Test");
    }

    @Test
    @DisplayName("getProduct: product not found → throws PRODUCT_NOT_FOUND")
    void getProduct_notFound_throwsAppException() {
        UUID id = UUID.randomUUID();
        given(productRepository.findByIdAndDeletedFalse(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(id))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(CustomCode.PRODUCT_NOT_FOUND.getDefaultMessage());
    }

    // ── createProduct ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProduct: duplicate slug → throws SLUG_ALREADY_EXISTS")
    void createProduct_duplicateSlug_throwsAppException() {
        UUID catId   = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        var request  = new ProductRequest(
                "Laptop ABC", "laptop-abc", "Description", catId, brandId,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(12_000_000),
                5, null, null, null, ProductStatus.ACTIVE
        );

        given(categoryRepository.findById(catId)).willReturn(Optional.of(stubCategory(catId)));
        given(brandRepository.findById(brandId)).willReturn(Optional.of(stubBrand(brandId)));
        given(productRepository.existsBySlug("laptop-abc")).willReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(CustomCode.SLUG_ALREADY_EXISTS.getDefaultMessage());

        then(productRepository).should(never()).save(any());
    }

    // ── updateProduct ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProduct: success → saves updated product")
    void updateProduct_success_savesUpdatedProduct() {
        UUID id      = UUID.randomUUID();
        UUID catId   = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        Product product = stubProduct(id);
        var request = new ProductRequest(
                "Laptop Updated", "laptop-updated", "New Desc", catId, brandId,
                BigDecimal.valueOf(11_000_000), BigDecimal.valueOf(13_000_000),
                8, null, null, null, ProductStatus.ACTIVE
        );

        given(productRepository.findByIdAndDeletedFalse(id)).willReturn(Optional.of(product));
        given(categoryRepository.findById(catId)).willReturn(Optional.of(stubCategory(catId)));
        given(brandRepository.findById(brandId)).willReturn(Optional.of(stubBrand(brandId)));
        given(productRepository.existsBySlugAndIdNot("laptop-updated", id)).willReturn(false);
        given(productRepository.save(any())).willReturn(product);

        productService.updateProduct(id, request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        then(productRepository).should().save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Laptop Updated");
    }

    // ── deleteProduct ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProduct: sets deleted=true and saves (soft delete)")
    void deleteProduct_setsDeletedFlagAndSaves() {
        UUID    id      = UUID.randomUUID();
        Product product = stubProduct(id);
        given(productRepository.findByIdAndDeletedFalse(id)).willReturn(Optional.of(product));
        given(productRepository.save(any())).willReturn(product);

        productService.deleteProduct(id);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        then(productRepository).should().save(captor.capture());
        assertThat(captor.getValue().getDeleted()).isTrue();
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private Product stubProduct(UUID id) {
        Category cat   = stubCategory(UUID.randomUUID());
        Brand    brand = stubBrand(UUID.randomUUID());
        Product  p     = new Product();
        p.setId(id);
        p.setName("Laptop Test");
        p.setSlug("laptop-test");
        p.setCategory(cat);
        p.setBrand(brand);
        p.setPrice(BigDecimal.valueOf(10_000_000));
        p.setOriginalPrice(BigDecimal.valueOf(12_000_000));
        p.setStock(10);
        p.setStatus(ProductStatus.ACTIVE);
        p.setDeleted(false);
        return p;
    }

    private Category stubCategory(UUID id) {
        Category c = new Category();
        c.setId(id);
        c.setName("Laptop");
        return c;
    }

    private Brand stubBrand(UUID id) {
        Brand b = new Brand();
        b.setId(id);
        b.setName("Dell");
        return b;
    }
}
