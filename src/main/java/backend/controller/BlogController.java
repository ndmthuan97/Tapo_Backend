package backend.controller;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import backend.dto.common.ApiResponse;
import backend.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

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
}
