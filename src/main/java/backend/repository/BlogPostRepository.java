package backend.repository;

import backend.model.entity.BlogPost;
import backend.model.enums.BlogPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    @Query("""
        SELECT p FROM BlogPost p
        JOIN FETCH p.category c
        JOIN FETCH p.author a
        WHERE p.status = :status
          AND (:categorySlug IS NULL OR c.slug = :categorySlug)
        ORDER BY p.publishedAt DESC
    """)
    Page<BlogPost> findPublished(BlogPostStatus status, String categorySlug, Pageable pageable);

    @Query("""
        SELECT p FROM BlogPost p
        JOIN FETCH p.category
        JOIN FETCH p.author
        WHERE p.slug = :slug AND p.status = :status
    """)
    Optional<BlogPost> findBySlugAndStatus(String slug, BlogPostStatus status);

    @Modifying
    @Query("UPDATE BlogPost p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(UUID id);
}
