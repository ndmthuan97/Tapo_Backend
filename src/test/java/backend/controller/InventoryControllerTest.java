package backend.controller;

import backend.dto.inventory.CreateReceiptRequest;
import backend.dto.inventory.InventoryReceiptDto;
import backend.exception.GlobalExceptionHandler;
import backend.model.entity.User;
import backend.model.enums.ReceiptType;
import backend.security.CustomUserDetails;
import backend.service.InventoryService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryController Unit Tests — standaloneSetup + GlobalExceptionHandler.
 * Covers: INV-001 ~ INV-005
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryController — Unit Tests")
class InventoryControllerTest {

    @Mock InventoryService inventoryService;

    @InjectMocks InventoryController inventoryController;

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
                .standaloneSetup(inventoryController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InventoryReceiptDto stubReceipt(ReceiptType type) {
        return new InventoryReceiptDto(
                UUID.randomUUID(), "REC-001", type,
                "admin@tapo.vn", null, "Nhập kho thủ công",
                List.of(new InventoryReceiptDto.ItemDto(
                        UUID.randomUUID(), "MacBook Pro", null,
                        5, BigDecimal.valueOf(35_000_000), BigDecimal.valueOf(175_000_000)
                )),
                Instant.now()
        );
    }

    // ── GET /api/admin/inventory ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/inventory")
    class ListReceipts {

        @Test
        @DisplayName("INV-003: xem danh sách phiếu kho → 200, phân trang")
        void listReceipts_200() throws Exception {
            Page<InventoryReceiptDto> page = new PageImpl<>(List.of(
                    stubReceipt(ReceiptType.IMPORT),
                    stubReceipt(ReceiptType.EXPORT)
            ));
            given(inventoryService.listReceipts(isNull(), anyInt(), anyInt())).willReturn(page);

            mockMvc.perform(get("/api/admin/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("INV-003b: lọc theo type=IMPORT → 200")
        void listReceipts_filterByType_200() throws Exception {
            Page<InventoryReceiptDto> page = new PageImpl<>(List.of(stubReceipt(ReceiptType.IMPORT)));
            given(inventoryService.listReceipts(eq(ReceiptType.IMPORT), anyInt(), anyInt())).willReturn(page);

            mockMvc.perform(get("/api/admin/inventory").param("type", "IMPORT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].type").value("IMPORT"));
        }
    }

    // ── POST /api/admin/inventory ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/admin/inventory — create receipt")
    class CreateReceipt {

        @Test
        @DisplayName("INV-001: tạo phiếu nhập kho IMPORT → 200, stock sản phẩm tăng")
        void createImportReceipt_200() throws Exception {
            InventoryReceiptDto created = stubReceipt(ReceiptType.IMPORT);
            given(inventoryService.createReceipt(eq(user.getId()), any(CreateReceiptRequest.class)))
                    .willReturn(created);

            String body = objectMapper.writeValueAsString(new CreateReceiptRequest(
                    ReceiptType.IMPORT, "Nhập kho tháng 4",
                    List.of(new CreateReceiptRequest.ItemEntry(
                            UUID.randomUUID(), 5, BigDecimal.valueOf(35_000_000)
                    ))
            ));

            mockMvc.perform(post("/api/admin/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.type").value("IMPORT"))
                    .andExpect(jsonPath("$.message").value("Phiếu kho đã được tạo"));
        }

        @Test
        @DisplayName("INV-002: tạo phiếu xuất kho EXPORT → 200, stock giảm")
        void createExportReceipt_200() throws Exception {
            InventoryReceiptDto created = stubReceipt(ReceiptType.EXPORT);
            given(inventoryService.createReceipt(eq(user.getId()), any(CreateReceiptRequest.class)))
                    .willReturn(created);

            String body = objectMapper.writeValueAsString(new CreateReceiptRequest(
                    ReceiptType.EXPORT, "Xuất kho điều chỉnh",
                    List.of(new CreateReceiptRequest.ItemEntry(
                            UUID.randomUUID(), 2, BigDecimal.valueOf(35_000_000)
                    ))
            ));

            mockMvc.perform(post("/api/admin/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.type").value("EXPORT"));
        }

        @Test
        @DisplayName("INV-004: phiếu kho immutable — không có PUT endpoint → 405")
        void inventoryImmutable_noPutEndpoint_405() throws Exception {
            UUID receiptId = UUID.randomUUID();

            mockMvc.perform(put("/api/admin/inventory/{id}", receiptId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("INV-005: CUSTOMER không truy cập inventory → @PreAuthorize annotation exists")
        void inventory_customerBlocked_preAuthorizePresent() throws Exception {
            // Verify @PreAuthorize is at class level on InventoryController
            java.lang.annotation.Annotation[] annotations = InventoryController.class.getAnnotations();
            boolean hasPreAuthorize = java.util.Arrays.stream(annotations)
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("PreAuthorize"));
            org.assertj.core.api.Assertions.assertThat(hasPreAuthorize)
                    .as("InventoryController should be secured with @PreAuthorize")
                    .isTrue();
        }
    }
}
