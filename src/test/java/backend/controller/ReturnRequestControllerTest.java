package backend.controller;

import backend.dto.returnrequest.CreateReturnRequest;
import backend.dto.returnrequest.ReturnRequestDto;
import backend.model.entity.User;
import backend.model.enums.ReturnRequestStatus;
import backend.security.CustomUserDetails;
import backend.service.impl.ReturnRequestServiceImpl;
import backend.util.TestDataFactory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReturnRequestController Unit Tests — standaloneSetup + SecurityContextHolder injection.
 *
 * Test cases: RETURN-001 to RETURN-008
 * Note: @PreAuthorize("hasRole('ADMIN')") on admin endpoints is NOT enforced in standaloneSetup.
 * Role enforcement is validated via SecurityConfig integration test (requires full Spring context).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnRequestController — Unit Tests")
class ReturnRequestControllerTest {

    @Mock ReturnRequestServiceImpl returnService;

    @InjectMocks ReturnRequestController returnRequestController;

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

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Page.getPageable() may return Pageable.unpaged() whose getOffset() throws
        // UnsupportedOperationException when Jackson tries to serialize it.
        // Ignoring 'pageable' breaks the cycle without losing meaningful test data.
        objectMapper.addMixIn(Page.class, PageSerializationMixin.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(returnRequestController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReturnRequestDto pendingReturnDto(UUID orderId) {
        return new ReturnRequestDto(
                UUID.randomUUID(), orderId, "ORD-001",
                user.getId(), "Test User",
                "Sản phẩm bị lỗi", Collections.emptyList(),
                ReturnRequestStatus.PENDING, null, Instant.now()
        );
    }

    // ── POST /api/orders/{orderId}/return ─────────────────────────────────────

    @Nested
    @DisplayName("POST /api/orders/{orderId}/return")
    class CreateReturn {

        // RETURN-001
        @Test
        @DisplayName("RETURN-001: createReturn with valid reason → 200 with PENDING status")
        void createReturn_success_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            CreateReturnRequest request = new CreateReturnRequest("Sản phẩm bị lỗi", Collections.emptyList());
            ReturnRequestDto dto = pendingReturnDto(orderId);
            given(returnService.createReturn(eq(user.getId()), eq(orderId), any())).willReturn(dto);

            mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.reason").value("Sản phẩm bị lỗi"))
                    .andExpect(jsonPath("$.message").value(
                            "Yêu cầu hoàn trả đã được gửi. Chúng tôi sẽ xử lý trong 3–5 ngày làm việc."));
        }

        // RETURN-007 — @NotBlank validation: blank reason → 400
        @Test
        @DisplayName("RETURN-007: createReturn with blank reason → 400 validation error")
        void createReturn_blankReason_400() throws Exception {
            UUID orderId = UUID.randomUUID();
            CreateReturnRequest request = new CreateReturnRequest("", Collections.emptyList());

            mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("createReturn with evidenceImages list → 200")
        void createReturn_withImages_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            CreateReturnRequest request = new CreateReturnRequest(
                    "Hàng bị vỡ khi nhận",
                    List.of("https://storage.example.com/evidence1.jpg"));
            ReturnRequestDto dto = new ReturnRequestDto(
                    UUID.randomUUID(), orderId, "ORD-002",
                    user.getId(), "Test User",
                    "Hàng bị vỡ khi nhận",
                    List.of("https://storage.example.com/evidence1.jpg"),
                    ReturnRequestStatus.PENDING, null, Instant.now()
            );
            given(returnService.createReturn(eq(user.getId()), eq(orderId), any())).willReturn(dto);

            mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.evidenceImages").isArray());
        }
    }

    // ── GET /api/orders/{orderId}/return ──────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders/{orderId}/return")
    class GetByOrder {

        // RETURN-002
        @Test
        @DisplayName("RETURN-002: getByOrder → 200 with ReturnRequestDto for this order")
        void getByOrder_success_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            ReturnRequestDto dto = pendingReturnDto(orderId);
            given(returnService.getByOrder(user.getId(), orderId)).willReturn(dto);

            mockMvc.perform(get("/api/orders/{orderId}/return", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.data.reason").value("Sản phẩm bị lỗi"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }
    }

    // ── GET /api/orders/returns ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/orders/returns")
    class GetMyReturns {

        // RETURN-003
        @Test
        @DisplayName("RETURN-003: getMyReturns → 200 with paged list of returns")
        void getMyReturns_success_200() throws Exception {
            UUID orderId = UUID.randomUUID();
            Page<ReturnRequestDto> page = new PageImpl<>(List.of(pendingReturnDto(orderId)));
            given(returnService.getMyReturns(eq(user.getId()), any())).willReturn(page);

            mockMvc.perform(get("/api/orders/returns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("getMyReturns empty list → 200 empty page")
        void getMyReturns_empty_200() throws Exception {
            given(returnService.getMyReturns(eq(user.getId()), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

            mockMvc.perform(get("/api/orders/returns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ── GET /api/admin/returns ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/returns")
    class ListAll {

        // RETURN-004
        @Test
        @DisplayName("RETURN-004: listAll without status filter → 200 with all returns")
        void listAll_noFilter_200() throws Exception {
            Page<ReturnRequestDto> page = new PageImpl<>(List.of(pendingReturnDto(UUID.randomUUID())));
            given(returnService.listAll(eq(null), any())).willReturn(page);

            mockMvc.perform(get("/api/admin/returns"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        // RETURN-008 — filter by status
        @Test
        @DisplayName("RETURN-008: listAll filtered by PENDING → 200 with only PENDING returns")
        void listAll_pendingFilter_200() throws Exception {
            Page<ReturnRequestDto> page = new PageImpl<>(List.of(pendingReturnDto(UUID.randomUUID())));
            given(returnService.listAll(eq(ReturnRequestStatus.PENDING), any())).willReturn(page);

            mockMvc.perform(get("/api/admin/returns")
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        }
    }

    // ── PATCH /api/admin/returns/{id}/status ─────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/returns/{id}/status")
    class UpdateStatus {

        // RETURN-005
        @Test
        @DisplayName("RETURN-005: updateStatus to APPROVED with note → 200")
        void updateStatus_approved_200() throws Exception {
            UUID returnId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ReturnRequestDto approved = new ReturnRequestDto(
                    returnId, orderId, "ORD-001",
                    user.getId(), "Test User",
                    "Sản phẩm bị lỗi", Collections.emptyList(),
                    ReturnRequestStatus.APPROVED, "Đã xác nhận", Instant.now()
            );
            given(returnService.updateStatus(
                    eq(user.getId()), eq(returnId), eq(ReturnRequestStatus.APPROVED), eq("Đã xác nhận")))
                    .willReturn(approved);

            mockMvc.perform(patch("/api/admin/returns/{id}/status", returnId)
                            .param("status", "APPROVED")
                            .param("note", "Đã xác nhận"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.staffNote").value("Đã xác nhận"))
                    .andExpect(jsonPath("$.message").value("Cập nhật trạng thái thành công"));
        }

        // RETURN-006 — note param is optional, defaults to ""
        @Test
        @DisplayName("RETURN-006: updateStatus to REJECTED without note → 200 (defaults to empty string)")
        void updateStatus_rejected_noNote_200() throws Exception {
            UUID returnId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ReturnRequestDto rejected = new ReturnRequestDto(
                    returnId, orderId, "ORD-001",
                    user.getId(), "Test User",
                    "Sản phẩm bị lỗi", Collections.emptyList(),
                    ReturnRequestStatus.REJECTED, "", Instant.now()
            );
            given(returnService.updateStatus(
                    eq(user.getId()), eq(returnId), eq(ReturnRequestStatus.REJECTED), eq("")))
                    .willReturn(rejected);

            mockMvc.perform(patch("/api/admin/returns/{id}/status", returnId)
                            .param("status", "REJECTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }
    }

    @JsonIgnoreProperties("pageable")
    abstract static class PageSerializationMixin {}
}
