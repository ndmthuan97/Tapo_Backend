package backend.dto.blog;

import java.time.Instant;
import java.util.UUID;

public record BlogPostDto(
        UUID id,
        String title,
        String slug,
        String thumbnailUrl,
        String excerpt,
        String content,
        Integer viewCount,
        String categoryName,
        String categorySlug,
        String authorName,
        String metaTitle,
        String metaDescription,
        Instant publishedAt,
        Instant createdAt
) {}
