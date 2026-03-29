package backend.service;

import backend.dto.product.ProductDto;
import backend.dto.product.ProductRequest;
import backend.dto.product.SimpleRefDto;
import backend.model.enums.ProductStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    Page<ProductDto> getProducts(int page, int size, String search, ProductStatus status);

    ProductDto getProduct(UUID id);

    ProductDto createProduct(ProductRequest request);

    ProductDto updateProduct(UUID id, ProductRequest request);

    void deleteProduct(UUID id);

    List<SimpleRefDto> getAllCategories();

    List<SimpleRefDto> getAllBrands();
}
