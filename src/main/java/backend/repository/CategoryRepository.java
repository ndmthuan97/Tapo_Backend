package backend.repository;

import backend.model.entity.Category;
import backend.model.enums.CatalogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByStatusOrderBySortOrderAsc(CatalogStatus status);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
}
