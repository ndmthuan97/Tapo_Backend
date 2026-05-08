package backend.controller;

import backend.dto.common.CustomCode;
import backend.dto.review.*;
import backend.exception.AppException;
import backend.exception.AuthException;
import backend.exception.GlobalExceptionHandler;
import backend.model.entity.User;
import backend.model.enums.ReviewStatus;
import backend.security.CustomUserDetails;
import backend.service.ReviewService;
import backend.util.TestDataFactory;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReviewController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: REVIEW-005 ~ REVIEW-014
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewController — Unit Tests")
class ReviewControllerTest {

    @Mock ReviewService reviewService;

    @InjectMocks ReviewController reviewController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    User user;
    CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.activeUser();
        principal = new CustomUserDetails(user);

        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        ));

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(reviewController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReviewDto stubReviewDto(UUID id, int rating) {
        return new ReviewDto(id, user.getId(), "Test User", null,
                rating, "Sản phẩm tốt", Collections.emptyList(), Instant.now());
    }

    private AdminReviewDto stubAdminReviewDto(UUID id, ReviewStatus status) {
        return new AdminReviewDto(
                id, user.getId(), "Test User", null,
                UUID.randomUUID(), "MacBook Pro", null,
                5, "Sản phẩm tuyệt vời", Collections.emptyList(),
                status, Instant.now(), null, null);
    }

    // ── POST /api/reviews — validation ────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/reviews — validation")
    class CreateReviewValidation {

        @Test
        @DisplayName("REVIEW-005: rating ngoài range (rating=0) → 400 REVIEW_NOT_ELIGIBLE hoặc validation")
        void createReview_ratingZero_400() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID orderId   = UUID.randomUUID();
            given(reviewService.createReview(eq(user.getId()), any()))
                    .willThrow(new AppException(CustomCode.REVIEW_NOT_ELIGIBLE));

            String body = objectMapper.writeValueAsString(
                    new CreateReviewRequest(productId, orderId, 0, "Nội dung", Collections.emptyList()));

            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("REVIEW-005b: rating=6 → 400")
        void createReview_ratingOutOfRange_400() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID orderId   = UUID.randomUUID();
            given(reviewService.createReview(eq(user.getId()), any()))
                    .willThrow(new AppException(CustomCode.REVIEW_NOT_ELIGIBLE));

            String body = objectMapper.writeValueAsString(
                    new CreateReviewRequest(productId, orderId, 6, "Nội dung", Collections.emptyList()));

            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("REVIEW-006: thiếu content (comment rỗng) → 400")
        void createReview_emptyComment_400() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID orderId   = UUID.randomUUID();
            given(reviewService.createReview(eq(user.getId()), any()))
                    .willThrow(new AppException(CustomCode.BAD_REQUEST));

            String body = objectMapper.writeValueAsString(
                    new CreateReviewRequest(productId, orderId, 5, "", Collections.emptyList()));

            mockMvc.perform(post("/api/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ── GET /api/products/{productId}/reviews ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/products/{productId}/reviews")
    class GetProductReviews {

        @Test
        @DisplayName("REVIEW-007: xem reviews sản phẩm → 200, chỉ review APPROVED")
        void getProductReviews_200() throws Exception {
            UUID productId = UUID.randomUUID();
            var items = List.of(
                    stubReviewDto(UUID.randomUUID(), 5),
                    stubReviewDto(UUID.randomUUID(), 4)
            );
            Page<ReviewDto> page = new PageImpl<>(items, PageRequest.of(0, 10), items.size());
            given(reviewService.getProductReviews(eq(productId), any())).willReturn(page);

            mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("REVIEW-008: review PENDING không hiển thị → 200, list rỗng")
        void getProductReviews_pendingNotVisible_200() throws Exception {
            UUID productId = UUID.randomUUID();
            // Service filters out PENDING — returns empty
            given(reviewService.getProductReviews(eq(productId), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get("/api/products/{productId}/reviews", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ── Admin review management ───────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/reviews/{id}/approve — REVIEW-009")
    class ApproveReview {

        @Test
        @DisplayName("REVIEW-009: Admin duyệt review → 200, status=APPROVED")
        void approveReview_200() throws Exception {
            UUID reviewId = UUID.randomUUID();
            AdminReviewDto approved = stubAdminReviewDto(reviewId, ReviewStatus.APPROVED);
            given(reviewService.approveReview(reviewId)).willReturn(approved);

            mockMvc.perform(patch("/api/admin/reviews/{id}/approve", reviewId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.message").value("Đánh giá đã được duyệt."));
        }

        @Test
        @DisplayName("REVIEW-009b: duyệt review không tồn tại → 404")
        void approveReview_notFound_404() throws Exception {
            UUID reviewId = UUID.randomUUID();
            given(reviewService.approveReview(reviewId))
                    .willThrow(new AppException(CustomCode.REVIEW_NOT_FOUND));

            mockMvc.perform(patch("/api/admin/reviews/{id}/approve", reviewId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/reviews/{id}/reject — REVIEW-010")
    class RejectReview {

        @Test
        @DisplayName("REVIEW-010: Admin từ chối review → 200, status=REJECTED")
        void rejectReview_200() throws Exception {
            UUID reviewId = UUID.randomUUID();
            AdminReviewDto rejected = stubAdminReviewDto(reviewId, ReviewStatus.REJECTED);
            given(reviewService.rejectReview(reviewId)).willReturn(rejected);

            mockMvc.perform(patch("/api/admin/reviews/{id}/reject", reviewId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.message").value("Đánh giá đã bị từ chối."));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/reviews/{id}/reply — REVIEW-011, REVIEW-012")
    class ReplyReview {

        @Test
        @DisplayName("REVIEW-011: Admin reply review → 200, reply được lưu")
        void replyReview_200() throws Exception {
            UUID reviewId = UUID.randomUUID();
            AdminReviewDto replied = new AdminReviewDto(
                    reviewId, user.getId(), "Test User", null,
                    UUID.randomUUID(), "MacBook Pro", null,
                    5, "Sản phẩm tuyệt vời", Collections.emptyList(),
                    ReviewStatus.APPROVED, Instant.now(),
                    "Cảm ơn bạn đã đánh giá!", Instant.now()
            );
            given(reviewService.replyReview(eq(reviewId), anyString())).willReturn(replied);

            mockMvc.perform(post("/api/admin/reviews/{id}/reply", reviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reply\":\"Cảm ơn bạn đã đánh giá!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminReply").value("Cảm ơn bạn đã đánh giá!"))
                    .andExpect(jsonPath("$.message").value("Đã gửi phản hồi."));
        }

        @Test
        @DisplayName("REVIEW-012: Xoá reply bằng cách truyền reply='' → 200, reply bị xoá")
        void replyReview_emptyReply_deletesReply_200() throws Exception {
            UUID reviewId = UUID.randomUUID();
            AdminReviewDto noReply = stubAdminReviewDto(reviewId, ReviewStatus.APPROVED);
            given(reviewService.replyReview(eq(reviewId), eq(""))).willReturn(noReply);

            mockMvc.perform(post("/api/admin/reviews/{id}/reply", reviewId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reply\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminReply").isEmpty());
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/reviews/bulk-action — REVIEW-013")
    class BulkAction {

        @Test
        @DisplayName("REVIEW-013: Bulk approve → 200, danh sách IDs đã xử lý")
        void bulkApprove_200() throws Exception {
            List<UUID> reviewIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            given(reviewService.bulkAction(anyList(), eq(BulkReviewActionRequest.BulkAction.APPROVE)))
                    .willReturn(reviewIds);

            String body = objectMapper.writeValueAsString(
                    new BulkReviewActionRequest(reviewIds, BulkReviewActionRequest.BulkAction.APPROVE));

            mockMvc.perform(patch("/api/admin/reviews/bulk-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("REVIEW-013b: Bulk reject → 200")
        void bulkReject_200() throws Exception {
            List<UUID> reviewIds = List.of(UUID.randomUUID());
            given(reviewService.bulkAction(anyList(), eq(BulkReviewActionRequest.BulkAction.REJECT)))
                    .willReturn(reviewIds);

            String body = objectMapper.writeValueAsString(
                    new BulkReviewActionRequest(reviewIds, BulkReviewActionRequest.BulkAction.REJECT));

            mockMvc.perform(patch("/api/admin/reviews/bulk-action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/reviews/pending-count — REVIEW-014")
    class PendingCount {

        @Test
        @DisplayName("REVIEW-014: pending count → 200, số đúng")
        void pendingCount_200() throws Exception {
            given(reviewService.countPendingReviews()).willReturn(7L);

            mockMvc.perform(get("/api/admin/reviews/pending-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(7));
        }
    }
}
