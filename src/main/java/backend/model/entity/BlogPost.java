package backend.model.entity;

import backend.model.enums.BlogPostStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "blog_posts", indexes = {
        @Index(name = "idx_blog_posts_slug", columnList = "slug", unique = true),
        @Index(name = "idx_blog_posts_status", columnList = "status"),
        @Index(name = "idx_blog_posts_category", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
public class BlogPost extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private BlogCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, unique = true, length = 600)
    private String slug;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "excerpt", columnDefinition = "TEXT")
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlogPostStatus status = BlogPostStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ===== SEO metadata =====

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;
}
