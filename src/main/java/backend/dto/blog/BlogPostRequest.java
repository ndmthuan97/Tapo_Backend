package backend.dto.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BlogPostRequest(
        @NotBlank String title,
        @NotBlank String slug,
                  String thumbnailUrl,
                  String excerpt,
        @NotBlank String content,
        @NotNull  UUID   categoryId,
                  String metaTitle,
                  String metaDescription,
                  boolean publish          // true = set publishedAt now, false = draft
) {}
