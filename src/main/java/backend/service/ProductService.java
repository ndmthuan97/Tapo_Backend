package backend.service;

import backend.dto.product.ProductDto;
import backend.dto.product.ProductRequest;
import backend.dto.product.SimpleRefDto;
import backend.dto.product.SuggestDto;
import backend.model.enums.ProductStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductService {

    Page<ProductDto> getProducts(int page, int size, String search, ProductStatus status,
                                 UUID categoryId, UUID brandId, Long minPrice, Long maxPrice,
                                 Double minRating, Boolean inStock, String sort);

    ProductDto getProduct(UUID id);

    List<ProductDto> getRelatedProducts(UUID productId, int limit);

    List<SuggestDto> suggestProducts(String query);

    ProductDto createProduct(ProductRequest request);

    ProductDto updateProduct(UUID id, ProductRequest request);

    void deleteProduct(UUID id);

    /** Delete multiple products. Silently ignores IDs that do not exist. */
    void bulkDelete(Set<UUID> ids);

    /** Update status for multiple products in one transaction. */
    void bulkUpdateStatus(Set<UUID> ids, ProductStatus status);

    List<SimpleRefDto> getAllCategories();

    List<SimpleRefDto> getAllBrands();
}
