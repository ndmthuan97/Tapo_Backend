package backend.service.impl;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.BlogCategory;
import backend.model.entity.BlogPost;
import backend.model.enums.BlogPostStatus;
import backend.repository.BlogCategoryRepository;
import backend.repository.BlogPostRepository;
import backend.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogPostRepository blogPostRepo;
    private final BlogCategoryRepository blogCategoryRepo;

    // ── Mapping ─────────────────────────────────────────────────────────────────

    private BlogCategoryDto toCategoryDto(BlogCategory c) {
        return new BlogCategoryDto(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getSortOrder());
    }

    private BlogPostDto toPostDto(BlogPost p) {
        return new BlogPostDto(
                p.getId(),
                p.getTitle(),
                p.getSlug(),
                p.getThumbnailUrl(),
                p.getExcerpt(),
                p.getContent(),
                p.getViewCount(),
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getSlug() : null,
                p.getAuthor() != null ? p.getAuthor().getFullName() : null,
                p.getMetaTitle(),
                p.getMetaDescription(),
                p.getPublishedAt(),
                p.getCreatedAt()
        );
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BlogCategoryDto> getCategories() {
        return blogCategoryRepo.findAllByOrderBySortOrderAsc()
                .stream().map(this::toCategoryDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlogPostDto> getPosts(String categorySlug, Pageable pageable) {
        return blogPostRepo.findPublished(BlogPostStatus.PUBLISHED, categorySlug, pageable)
                .map(this::toPostDto);
    }

    @Override
    @Transactional
    public BlogPostDto getPostBySlug(String slug) {
        BlogPost post = blogPostRepo.findBySlugAndStatus(slug, BlogPostStatus.PUBLISHED)
                .orElseThrow(() -> new AuthException(CustomCode.BLOG_NOT_FOUND));
        blogPostRepo.incrementViewCount(post.getId());
        return toPostDto(post);
    }
}
