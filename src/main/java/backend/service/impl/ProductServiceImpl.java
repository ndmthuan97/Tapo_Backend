package backend.service.impl;

import backend.dto.product.ProductDto;
import backend.dto.product.ProductRequest;
import backend.dto.product.SimpleRefDto;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Brand;
import backend.model.entity.Category;
import backend.model.entity.Product;
import backend.model.enums.ProductStatus;
import backend.repository.BrandRepository;
import backend.repository.CategoryRepository;
import backend.repository.ProductRepository;
import backend.service.ProductService;
import backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    // ── Read ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDto> getProducts(int page, int size, String search, ProductStatus status,
                                        UUID categoryId, UUID brandId, Long minPrice, Long maxPrice,
                                        String sort) {
        Sort sortObj = parseSort(sort);
        PageRequest pageable = PageRequest.of(page, size, sortObj);
        String searchParam = (search != null && search.isBlank()) ? null : search;
        return productRepository
                .searchProducts(searchParam, status, categoryId, brandId, minPrice, maxPrice, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProduct(UUID id) {
        return toDto(findActiveProduct(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getRelatedProducts(UUID productId, int limit) {
        Product product = findActiveProduct(productId);
        UUID categoryId = product.getCategory().getId();
        PageRequest pageable = PageRequest.of(0, limit);
        return productRepository.findRelated(categoryId, productId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Write ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        Category category = findCategory(request.categoryId());
        Brand brand       = findBrand(request.brandId());
        String slug       = resolveSlug(request.slug(), request.name(), null);

        Product product = new Product();
        product.setName(request.name());
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setCategory(category);
        product.setBrand(brand);
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setStock(request.stock() != null ? request.stock() : 0);
        product.setThumbnailUrl(request.thumbnailUrl());
        product.setSpecifications(request.specifications());
        product.setStatus(request.status() != null ? request.status() : ProductStatus.DRAFT);

        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDto updateProduct(UUID id, ProductRequest request) {
        Product product   = findActiveProduct(id);
        Category category = findCategory(request.categoryId());
        Brand brand       = findBrand(request.brandId());
        String slug       = resolveSlug(request.slug(), request.name(), id);

        product.setName(request.name());
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setCategory(category);
        product.setBrand(brand);
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setStock(request.stock() != null ? request.stock() : product.getStock());
        product.setThumbnailUrl(request.thumbnailUrl());
        product.setSpecifications(request.specifications());
        if (request.status() != null) product.setStatus(request.status());

        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findActiveProduct(id);
        product.setDeleted(true);
        productRepository.save(product);
    }

    // ── Metadata ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SimpleRefDto> getAllCategories() {
        return categoryRepository.findAllByIsVisibleTrueOrderBySortOrderAsc()
                .stream()
                .map(c -> new SimpleRefDto(c.getId(), c.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimpleRefDto> getAllBrands() {
        return brandRepository.findAllByIsVisibleTrueOrderByNameAsc()
                .stream()
                .map(b -> new SimpleRefDto(b.getId(), b.getName()))
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private Product findActiveProduct(UUID id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(CustomCode.PRODUCT_NOT_FOUND));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CustomCode.CATEGORY_NOT_FOUND));
    }

    private Brand findBrand(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new AppException(CustomCode.BRAND_NOT_FOUND));
    }

    /**
     * Parse sort string like "price,asc" or "createdAt,desc" into a Sort object.
     * Defaults to createdAt desc.
     */
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by("createdAt").descending();
        String[] parts = sort.split(",");
        String field     = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim() : "desc";
        // Whitelist allowed sort fields to prevent injection
        String safeField = switch (field) {
            case "price"     -> "price";
            case "name"      -> "name";
            case "soldCount" -> "soldCount";
            case "avgRating" -> "avgRating";
            default          -> "createdAt";
        };
        return "asc".equalsIgnoreCase(direction)
                ? Sort.by(safeField).ascending()
                : Sort.by(safeField).descending();
    }

    /**
     * Resolves the final slug:
     *  1. If request provides a non-blank slug → use it (after sanitising)
     *  2. Otherwise → auto-generate from product name
     * Then validates uniqueness (excluding current product on update).
     */
    private String resolveSlug(String requestedSlug, String name, UUID excludeId) {
        String slug = (requestedSlug != null && !requestedSlug.isBlank())
                ? SlugUtils.toSlug(requestedSlug)
                : SlugUtils.toSlug(name);

        boolean taken = (excludeId == null)
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, excludeId);

        if (taken) throw new AppException(CustomCode.SLUG_ALREADY_EXISTS);
        return slug;
    }

    private ProductDto toDto(Product p) {
        return new ProductDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getDescription(),
                p.getPrice(),
                p.getOriginalPrice(),
                p.getStock(),
                p.getStatus(),
                p.getThumbnailUrl(),
                p.getAvgRating(),
                p.getReviewCount(),
                p.getSoldCount(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getBrand().getId(),
                p.getBrand().getName(),
                p.getSpecifications(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
