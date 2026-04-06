package backend.service.impl;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import backend.dto.blog.BlogPostRequest;
import backend.dto.common.CustomCode;
import backend.exception.AuthException;
import backend.model.entity.BlogCategory;
import backend.model.entity.BlogPost;
import backend.model.entity.User;
import backend.model.enums.BlogPostStatus;
import backend.repository.BlogCategoryRepository;
import backend.repository.BlogPostRepository;
import backend.repository.UserRepository;
import backend.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogPostRepository    blogPostRepo;
    private final BlogCategoryRepository blogCategoryRepo;
    private final UserRepository         userRepo;

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
                p.getAuthor()   != null ? p.getAuthor().getFullName() : null,
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

    // ── Admin API ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<BlogPostDto> getAllPostsAdmin(Pageable pageable) {
        return blogPostRepo.findAllForAdmin(pageable).map(this::toPostDto);
    }

    @Override
    @Transactional
    public BlogPostDto createPost(BlogPostRequest req, String authorEmail) {
        User author = userRepo.findByEmail(authorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Author not found"));
        BlogCategory category = blogCategoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

        BlogPost post = new BlogPost();
        applyRequest(post, req, category);
        post.setAuthor(author);
        if (req.publish()) {
            post.setStatus(BlogPostStatus.PUBLISHED);
            post.setPublishedAt(Instant.now());
        }
        return toPostDto(blogPostRepo.save(post));
    }

    @Override
    @Transactional
    public BlogPostDto updatePost(UUID id, BlogPostRequest req) {
        BlogPost post = blogPostRepo.findByIdWithJoins(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        BlogCategory category = blogCategoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

        applyRequest(post, req, category);
        if (req.publish() && post.getPublishedAt() == null) {
            post.setStatus(BlogPostStatus.PUBLISHED);
            post.setPublishedAt(Instant.now());
        } else if (!req.publish()) {
            post.setStatus(BlogPostStatus.DRAFT);
            post.setPublishedAt(null);
        }
        return toPostDto(blogPostRepo.save(post));
    }

    @Override
    @Transactional
    public void deletePost(UUID id) {
        if (!blogPostRepo.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        blogPostRepo.deleteById(id);
    }

    @Override
    @Transactional
    public BlogPostDto togglePublish(UUID id) {
        BlogPost post = blogPostRepo.findByIdWithJoins(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        if (post.getStatus() == BlogPostStatus.PUBLISHED) {
            post.setStatus(BlogPostStatus.DRAFT);
            post.setPublishedAt(null);
        } else {
            post.setStatus(BlogPostStatus.PUBLISHED);
            post.setPublishedAt(Instant.now());
        }
        return toPostDto(blogPostRepo.save(post));
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private void applyRequest(BlogPost post, BlogPostRequest req, BlogCategory category) {
        post.setTitle(req.title());
        post.setSlug(req.slug());
        post.setThumbnailUrl(req.thumbnailUrl());
        post.setExcerpt(req.excerpt());
        post.setContent(req.content());
        post.setCategory(category);
        post.setMetaTitle(req.metaTitle());
        post.setMetaDescription(req.metaDescription());
    }
}
