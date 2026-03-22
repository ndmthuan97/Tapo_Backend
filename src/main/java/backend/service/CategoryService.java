package backend.service;

import backend.dto.category.CategoryDto;
import backend.dto.category.CategoryRequest;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    List<CategoryDto> getAllCategories();
    CategoryDto getCategory(UUID id);
    CategoryDto createCategory(CategoryRequest request);
    CategoryDto updateCategory(UUID id, CategoryRequest request);
    void deleteCategory(UUID id);
}
