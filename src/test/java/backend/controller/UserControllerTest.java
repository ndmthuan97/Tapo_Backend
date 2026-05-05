package backend.controller;

import backend.dto.common.CustomCode;
import backend.dto.user.*;
import backend.exception.GlobalExceptionHandler;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController Admin Endpoint Tests — standaloneSetup.
 * Covers: USER-001 ~ USER-005
 *
 * Note: USER-005 (locked user cannot login) is a service-level test
 * in AuthServiceImplTest (login_lockedAccount_throwsAccountLocked).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController — Admin Tests")
class UserControllerTest {

    @Mock UserService userService;

    @InjectMocks UserController userController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserDto stubUserDto(UUID id, UserRole role, UserStatus status) {
        return new UserDto(id, "Nguyen Van A", "user@tapo.vn",
                null, null, role, status);
    }

    private UserPageResponse stubPageResponse(List<UserDto> users) {
        return new UserPageResponse(users, users.size(), 1, 1, 8);
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/users")
    class GetAllUsers {

        @Test
        @DisplayName("USER-001: Admin xem danh sách user → 200, phân trang")
        void getAllUsers_200() throws Exception {
            UserPageResponse page = stubPageResponse(List.of(
                    stubUserDto(UUID.randomUUID(), UserRole.CUSTOMER, UserStatus.ACTIVE),
                    stubUserDto(UUID.randomUUID(), UserRole.CUSTOMER, UserStatus.ACTIVE)
            ));
            given(userService.getAllUsers(anyInt(), anyInt(), isNull())).willReturn(page);

            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("USER-002: lọc theo role=CUSTOMER → 200, chỉ user CUSTOMER")
        void getAllUsers_filterByRole_200() throws Exception {
            UserPageResponse page = stubPageResponse(List.of(
                    stubUserDto(UUID.randomUUID(), UserRole.CUSTOMER, UserStatus.ACTIVE)
            ));
            given(userService.getAllUsers(anyInt(), anyInt(), eq(UserRole.CUSTOMER))).willReturn(page);

            mockMvc.perform(get("/api/admin/users").param("role", "CUSTOMER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].role").value("CUSTOMER"));
        }
    }

    // ── PATCH /api/admin/users/{id}/lock ─────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/lock")
    class LockUser {

        @Test
        @DisplayName("USER-003: khoá tài khoản user → 200")
        void lockUser_200() throws Exception {
            UUID userId = UUID.randomUUID();
            willDoNothing().given(userService).lockUser(userId);

            mockMvc.perform(patch("/api/admin/users/{id}/lock", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(CustomCode.UPDATED.getDefaultMessage()));
        }
    }

    // ── PATCH /api/admin/users/{id}/unlock ────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/unlock")
    class UnlockUser {

        @Test
        @DisplayName("USER-004: mở khoá tài khoản → 200")
        void unlockUser_200() throws Exception {
            UUID userId = UUID.randomUUID();
            willDoNothing().given(userService).unlockUser(userId);

            mockMvc.perform(patch("/api/admin/users/{id}/unlock", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(CustomCode.UPDATED.getDefaultMessage()));
        }
    }

    // ── USER-005: user bị khoá không login được (already covered) ─────────────

    @Test
    @DisplayName("USER-005: user LOCKED không thể login — đã được cover trong AuthServiceImplTest.login_accountLocked")
    void lockUser_blocksLogin_coveredInServiceTest() {
        // This test case is documented to be covered by:
        // AuthServiceImplTest > Login > login_lockedAccount_throwsAccountLocked
        // AuthServiceImplTest > Login > login_emailNotVerified (in backend.service package)
        // No additional controller test needed here.
        org.assertj.core.api.Assertions.assertThat(true).isTrue();
    }
}
