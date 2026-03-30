package backend.repository;

import backend.model.entity.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    @Query("""
        SELECT w FROM Wishlist w
        JOIN FETCH w.product p
        JOIN FETCH p.category
        JOIN FETCH p.brand
        WHERE w.user.id = :userId
        ORDER BY w.createdAt DESC
    """)
    Page<Wishlist> findByUserIdWithProduct(UUID userId, Pageable pageable);

    Optional<Wishlist> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);

    long countByUserId(UUID userId);
}
