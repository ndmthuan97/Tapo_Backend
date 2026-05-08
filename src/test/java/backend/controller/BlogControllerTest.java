package backend.controller;

import backend.dto.blog.*;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.exception.GlobalExceptionHandler;
import backend.service.BlogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BlogController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: BLOG-001 ~ BLOG-007
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BlogController — Unit Tests")
class BlogControllerTest {

    @Mock BlogService blogService;

    @InjectMocks BlogController blogController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(blogController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BlogPostDto stubPost(String slug, boolean published) {
        return new BlogPostDto(
                UUID.randomUUID(), "Test Post", slug,
                "https://cdn.tapo.vn/thumb.jpg", "Excerpt...", "<p>Content</p>",
                42, "Tin tức", "tin-tuc", "admin@tapo.vn",
                "Meta Title", "Meta Description",
                published ? Instant.now().minusSeconds(3600) : null,
                Instant.now()
        );
    }

    // ── GET /api/blog ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/blog")
    class GetPosts {

        @Test
        @DisplayName("BLOG-001: xem danh sách bài viết → 200, chỉ published, default page=9")
        void getPosts_default_200() throws Exception {
            var items = List.of(stubPost("bai-viet-1", true), stubPost("bai-viet-2", true));
            Page<BlogPostDto> page = new PageImpl<>(items, PageRequest.of(0, 10), items.size());
            given(blogService.getPosts(isNull(), any())).willReturn(page);

            mockMvc.perform(get("/api/blog"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("BLOG-002: lọc theo category slug → 200, đúng category")
        void getPosts_filterByCategory_200() throws Exception {
            var items = List.of(stubPost("tin-1", true));
            Page<BlogPostDto> page = new PageImpl<>(items, PageRequest.of(0, 10), items.size());
            given(blogService.getPosts(eq("tin-tuc"), any())).willReturn(page);

            mockMvc.perform(get("/api/blog").param("categorySlug", "tin-tuc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].categorySlug").value("tin-tuc"));
        }
    }

    // ── GET /api/blog/{slug} ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/blog/{slug}")
    class GetPostBySlug {

        @Test
        @DisplayName("BLOG-003: xem bài viết theo slug → 200, viewCount +1")
        void getPostBySlug_200() throws Exception {
            BlogPostDto post = stubPost("huong-dan-chon-laptop", true);
            given(blogService.getPostBySlug("huong-dan-chon-laptop")).willReturn(post);

            mockMvc.perform(get("/api/blog/{slug}", "huong-dan-chon-laptop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slug").value("huong-dan-chon-laptop"));
        }

        @Test
        @DisplayName("BLOG-004: bài viết draft không hiển thị public → 404")
        void getPostBySlug_draft_404() throws Exception {
            given(blogService.getPostBySlug("draft-post"))
                    .willThrow(new AppException(CustomCode.BLOG_NOT_FOUND));

            mockMvc.perform(get("/api/blog/{slug}", "draft-post"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/blog — admin create")
    class CreatePost {

        @Test
        @DisplayName("BLOG-005: tạo bài viết → 200, isPublished=false mặc định")
        void createPost_200() throws Exception {
            BlogPostDto created = stubPost("new-post", false);
            given(blogService.createPost(any(), anyString())).willReturn(created);

            String body = """
                    {"title":"New Post","content":"Content...","slug":"new-post"}
                    """;

            mockMvc.perform(post("/api/blog")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .principal(() -> "admin@tapo.vn"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Post created"));
        }
    }

    @Nested
    @DisplayName("Admin — duplicate slug & toggle publish")
    class AdminBlogManagement {

        @Test
        @DisplayName("BLOG-006: slug đã tồn tại → 409 SLUG_ALREADY_EXISTS")
        void createPost_duplicateSlug_409() throws Exception {
            given(blogService.createPost(any(), anyString()))
                    .willThrow(new AppException(CustomCode.SLUG_ALREADY_EXISTS));

            String body = """
                    {"title":"Bài viết trùng slug","content":"...","slug":"existing-slug"}
                    """;

            mockMvc.perform(post("/api/blog")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .principal(() -> "admin@tapo.vn"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("BLOG-007: toggle publish/draft → 200, isPublished đảo chiều")
        void togglePublish_200() throws Exception {
            UUID postId = UUID.randomUUID();
            BlogPostDto toggled = stubPost("my-post", true);
            given(blogService.togglePublish(postId)).willReturn(toggled);

            mockMvc.perform(patch("/api/blog/{id}/publish", postId))
                    .andExpect(status().isOk());
        }
    }
}
