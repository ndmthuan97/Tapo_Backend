package backend.service;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import backend.dto.blog.BlogPostRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BlogService {

    List<BlogCategoryDto> getCategories();
    Page<BlogPostDto> getPosts(String categorySlug, Pageable pageable);
    BlogPostDto getPostBySlug(String slug);

    // ── Admin ────────────────────────────────────────────────────────────────
    Page<BlogPostDto>  getAllPostsAdmin(Pageable pageable);
    BlogPostDto        createPost(BlogPostRequest req, String authorEmail);
    BlogPostDto        updatePost(UUID id, BlogPostRequest req);
    void               deletePost(UUID id);
    BlogPostDto        togglePublish(UUID id);
}
