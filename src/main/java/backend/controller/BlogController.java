package backend.controller;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import backend.dto.blog.BlogPostRequest;
import backend.dto.common.ApiResponse;
import backend.service.BlogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
@Tag(name = "Blog", description = "Quản lý bài viết Blog")
public class BlogController {

    private final BlogService blogService;

    // ── Public ───────────────────────────────────────────────────────────────────

    /** GET /api/blog/categories */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<BlogCategoryDto>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(blogService.getCategories()));
    }

    /** GET /api/blog?page=0&size=9&categorySlug=xxx */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BlogPostDto>>> getPosts(
            @RequestParam(required = false) String categorySlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(blogService.getPosts(categorySlug, pageable)));
    }

    /** GET /api/blog/{slug} */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BlogPostDto>> getPostBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(blogService.getPostBySlug(slug)));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────────

    /** GET /api/blog/admin?page=0&size=10 — tất cả bài viết (kể cả draft) */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<BlogPostDto>>> getAllAdmin(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                blogService.getAllPostsAdmin(PageRequest.of(page, size))));
    }

    /** POST /api/blog — tạo bài viết mới */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BlogPostDto>> createPost(
            @Valid @RequestBody BlogPostRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success("Post created",
                blogService.createPost(req, principal.getUsername())));
    }

    /** PUT /api/blog/{id} — cập nhật bài viết */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BlogPostDto>> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody BlogPostRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Post updated",
                blogService.updatePost(id, req)));
    }

    /** PATCH /api/blog/{id}/publish — toggle published/draft */
    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BlogPostDto>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post status toggled",
                blogService.togglePublish(id)));
    }

    /** DELETE /api/blog/{id} */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable UUID id) {
        blogService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.success("Post deleted", null));
    }
}
