package backend.service.impl;

import backend.dto.category.CategoryDto;
import backend.dto.category.CategoryRequest;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Category;
import backend.model.enums.CatalogStatus;
import backend.repository.CategoryRepository;
import backend.service.CategoryService;
import backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CategoryService implementation with Spring Cache integration.
 *
 * <p>java-pro: Cache strategy:
 * <ul>
 *   <li>{@code metadata::all-categories} — full list for admin CRUD (no filter), TTL 30 min</li>
 *   <li>{@code metadata::categories} — active-only list used on product filter/shop, TTL 30 min</li>
 * </ul>
 * Write operations evict both keys to keep data consistent.
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    // java-pro: Cache full list (admin) separately from active-only list (shop/product filter)
    @Cacheable(value = "metadata", key = "'all-categories'")
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto getCategory(UUID id) {
        return toDto(findById(id));
    }

    @Override
    @Transactional
    // Evict both cache keys: admin list + active-only list (used in ProductService)
    @CacheEvict(value = "metadata", allEntries = true)
    public CategoryDto createCategory(CategoryRequest request) {
        String slug = resolveSlug(request.slug(), request.name(), null);

        Category category = new Category();
        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        category.setStatus(request.status() != null ? request.status() : CatalogStatus.ACTIVE);

        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @CacheEvict(value = "metadata", allEntries = true)
    public CategoryDto updateCategory(UUID id, CategoryRequest request) {
        Category category = findById(id);
        String slug = resolveSlug(request.slug(), request.name(), id);

        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        if (request.sortOrder() != null) category.setSortOrder(request.sortOrder());
        if (request.status() != null)    category.setStatus(request.status());

        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @CacheEvict(value = "metadata", allEntries = true)
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) throw new AppException(CustomCode.CATEGORY_NOT_FOUND);
        categoryRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Category findById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CustomCode.CATEGORY_NOT_FOUND));
    }

    private String resolveSlug(String requested, String name, UUID excludeId) {
        String slug = (requested != null && !requested.isBlank())
                ? SlugUtils.toSlug(requested)
                : SlugUtils.toSlug(name);

        boolean taken = categoryRepository.existsBySlugAndIdNot(slug, excludeId == null ? UUID.randomUUID() : excludeId);
        if (excludeId == null) taken = categoryRepository.existsBySlug(slug);
        if (taken) throw new AppException(CustomCode.SLUG_ALREADY_EXISTS);
        return slug;
    }

    private CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                c.getImageUrl(), c.getSortOrder(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
