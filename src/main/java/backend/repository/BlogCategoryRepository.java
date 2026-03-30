package backend.repository;

import backend.model.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {

    List<BlogCategory> findAllByOrderBySortOrderAsc();
}
