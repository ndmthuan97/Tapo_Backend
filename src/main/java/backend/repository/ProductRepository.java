package backend.repository;

import backend.model.entity.Product;
import backend.model.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** Find non-deleted product by ID */
    Optional<Product> findByIdAndDeletedFalse(UUID id);

    /**
     * Paginated search across name / brand / category, filtered by status and not deleted.
     * Uses LOWER() for case-insensitive search.
     */
    @Query(
        value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            JOIN FETCH p.brand b
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR
                   LOWER(p.name)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(b.name)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(c.name)   LIKE LOWER(CONCAT('%', :search, '%')))
            """,
        countQuery = """
            SELECT COUNT(p) FROM Product p
            JOIN p.category c
            JOIN p.brand b
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR
                   LOWER(p.name)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(b.name)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(c.name)   LIKE LOWER(CONCAT('%', :search, '%')))
            """
    )
    Page<Product> searchProducts(
            @Param("search") String search,
            @Param("status") ProductStatus status,
            Pageable pageable
    );
}
