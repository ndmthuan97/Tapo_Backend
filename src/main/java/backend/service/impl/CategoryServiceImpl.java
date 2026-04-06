package backend.service.impl;

import backend.dto.category.CategoryDto;
import backend.dto.category.CategoryRequest;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Category;
import backend.repository.CategoryRepository;
import backend.service.CategoryService;
import backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
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
    public CategoryDto createCategory(CategoryRequest request) {
        String slug = resolveSlug(request.slug(), request.name(), null);

        Category category = new Category();
        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        category.setStatus(request.status() != null ? request.status() : backend.model.enums.CatalogStatus.ACTIVE);

        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(UUID id, CategoryRequest request) {
        Category category = findById(id);
        String slug = resolveSlug(request.slug(), request.name(), id);

        category.setName(request.name());
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        if (request.sortOrder() != null) category.setSortOrder(request.sortOrder());
        if (request.status() != null) category.setStatus(request.status());

        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
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
