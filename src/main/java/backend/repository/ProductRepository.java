package backend.repository;

import backend.model.entity.Product;
import backend.model.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** Find non-deleted product by ID */
    Optional<Product> findByIdAndDeletedFalse(UUID id);

    /**
     * Paginated search with full filter support:
     * keyword, status, categoryId, brandId, price range.
     */
    @Query(
        value = """
            SELECT p FROM Product p
            JOIN FETCH p.category c
            JOIN FETCH p.brand b
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:brandId IS NULL OR b.id = :brandId)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:search IS NULL OR
                   LOWER(p.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(b.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(c.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """,
        countQuery = """
            SELECT COUNT(p) FROM Product p
            JOIN p.category c
            JOIN p.brand b
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:brandId IS NULL OR b.id = :brandId)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:search IS NULL OR
                   LOWER(p.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(b.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR
                   LOWER(c.name)   LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """
    )
    Page<Product> searchProducts(
            @Param("search")     String search,
            @Param("status")     ProductStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("brandId")    UUID brandId,
            @Param("minPrice")   Long minPrice,
            @Param("maxPrice")   Long maxPrice,
            Pageable pageable
    );

    /** Related products: same category, excluding current product, ACTIVE only */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category c
            JOIN FETCH p.brand b
            WHERE p.deleted = false
              AND p.status = backend.model.enums.ProductStatus.ACTIVE
              AND c.id = :categoryId
              AND p.id <> :excludeId
            """)
    List<Product> findRelated(
            @Param("categoryId") UUID categoryId,
            @Param("excludeId")  UUID excludeId,
            Pageable pageable
    );
}
