package backend.service;

import backend.dto.blog.BlogCategoryDto;
import backend.dto.blog.BlogPostDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BlogService {

    List<BlogCategoryDto> getCategories();

    Page<BlogPostDto> getPosts(String categorySlug, Pageable pageable);

    BlogPostDto getPostBySlug(String slug);
}
