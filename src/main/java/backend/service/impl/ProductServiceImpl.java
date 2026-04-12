package backend.service.impl;

import backend.dto.product.ProductDto;
import backend.dto.product.ProductImageDto;
import backend.dto.product.ProductRequest;
import backend.dto.product.SimpleRefDto;
import backend.dto.product.SuggestDto;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Brand;
import backend.model.entity.Category;
import backend.model.entity.Product;
import backend.model.entity.ProductImage;
import backend.model.enums.ProductStatus;
import backend.repository.BrandRepository;
import backend.repository.CategoryRepository;
import backend.repository.ProductRepository;
import backend.service.ProductService;
import backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
                                        Double minRating, Boolean inStock, String sort) {
        Sort sortObj = parseSort(sort);
        PageRequest pageable = PageRequest.of(page, size, sortObj);
        String searchParam = (search != null && search.isBlank()) ? null : search;
        return productRepository
                .searchProducts(searchParam, status, categoryId, brandId, minPrice, maxPrice, minRating, inStock, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    // java-pro: JOIN FETCH images to prevent N+1 queries on product detail page
    @Cacheable(value = "product-detail", key = "#id")
    public ProductDto getProduct(UUID id) {
        Product product = productRepository.findByIdWithImages(id)
                .orElseThrow(() -> new AppException(CustomCode.PRODUCT_NOT_FOUND));
        return toDto(product);
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

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "product-suggest", key = "#query", unless = "#result.isEmpty()")
    public List<SuggestDto> suggestProducts(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return productRepository.findTop5ByNameContainingIgnoreCaseAndStatusAndDeletedFalseOrderByNameAsc(
                        query.trim(), ProductStatus.ACTIVE)
                .stream()
                .map(p -> new SuggestDto(p.getId(), p.getName(), p.getSlug(), p.getThumbnailUrl(), p.getPrice()))
                .toList();
    }

    // ── Write ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
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

        if (request.status() != null) product.setStatus(request.status());

        syncImages(product, request.imageUrls());
        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "product-detail", key = "#id"),
        @CacheEvict(value = "products",       allEntries = true)
    })
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

        syncImages(product, request.imageUrls());
        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "product-detail", key = "#id"),
        @CacheEvict(value = "products",       allEntries = true)
    })
    public void deleteProduct(UUID id) {
        Product product = findActiveProduct(id);
        product.setDeleted(true);
        productRepository.save(product);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products",       allEntries = true),
        @CacheEvict(value = "product-suggest", allEntries = true)
    })
    public void bulkDelete(Set<UUID> ids) {
        // Fetch only existing products to avoid exceptions for missing IDs (idempotent)
        List<Product> targets = productRepository.findAllById(ids);
        targets.forEach(p -> p.setDeleted(true));
        productRepository.saveAll(targets);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products",       allEntries = true),
        @CacheEvict(value = "product-detail", allEntries = true)
    })
    public void bulkUpdateStatus(Set<UUID> ids, ProductStatus status) {
        List<Product> targets = productRepository.findAllById(ids);
        targets.forEach(p -> p.setStatus(status));
        productRepository.saveAll(targets);
    }

    // ── Metadata ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    // java-pro: metadata (categories/brands) changes infrequently -- 30min TTL in RedisConfig
    @Cacheable(value = "metadata", key = "'categories'")
    public List<SimpleRefDto> getAllCategories() {
        return categoryRepository.findAllByStatusOrderBySortOrderAsc(backend.model.enums.CatalogStatus.ACTIVE)
                .stream()
                .map(c -> new SimpleRefDto(c.getId(), c.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "metadata", key = "'brands'")
    public List<SimpleRefDto> getAllBrands() {
        return brandRepository.findAllByStatusOrderByNameAsc(backend.model.enums.CatalogStatus.ACTIVE)
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
        List<ProductImageDto> imageDtos = p.getImages().stream()
                .map(img -> new ProductImageDto(img.getId(), img.getImageUrl(), img.getAltText(), img.getSortOrder()))
                .toList();
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
                imageDtos,
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

    /**
     * Sync gallery images from request to entity.
     * Uses clear + addAll pattern leveraging orphanRemoval=true on the collection.
     * java-pro: avoids N+1 by operating on in-memory collection.
     */
    private void syncImages(Product product, List<ProductRequest.ImageEntry> entries) {
        product.getImages().clear();
        if (entries == null || entries.isEmpty()) return;
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ProductRequest.ImageEntry entry = entries.get(i);
            if (entry.url() == null || entry.url().isBlank()) continue;
            ProductImage img = new ProductImage();
            img.setProduct(product);
            img.setImageUrl(entry.url().trim());
            img.setAltText(entry.altText());
            img.setSortOrder(i);
            images.add(img);
        }
        product.getImages().addAll(images);
    }
}
