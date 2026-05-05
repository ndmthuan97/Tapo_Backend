package backend.controller;

import backend.model.enums.ReturnRequestStatus;
import backend.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorization annotation verification tests — AUTHZ-001 to AUTHZ-007.
 *
 * These tests use reflection to verify that @PreAuthorize annotations are present
 * on the correct controller methods. This catches accidental removal of security
 * annotations without requiring a full Spring Security context (and thus Redis).
 *
 * Full HTTP-level enforcement (401/403 responses) is validated via SecurityConfig
 * integration tests that run against a real application context.
 */
@DisplayName("Authorization — @PreAuthorize annotation verification")
class AuthorizationTest {

    // ── ReturnRequestController admin endpoints ────────────────────────────────

    @Nested
    @DisplayName("ReturnRequestController — admin @PreAuthorize")
    class ReturnRequestAdminAuth {

        // AUTHZ-001
        @Test
        @DisplayName("AUTHZ-001: listAll endpoint has @PreAuthorize hasRole('ADMIN')")
        void listAll_hasAdminPreAuthorize() throws NoSuchMethodException {
            Method method = ReturnRequestController.class.getDeclaredMethod(
                    "listAll", ReturnRequestStatus.class, int.class, int.class);

            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation).as("@PreAuthorize must be present on listAll").isNotNull();
            assertThat(annotation.value()).contains("ADMIN");
        }

        // AUTHZ-002
        @Test
        @DisplayName("AUTHZ-002: updateStatus endpoint has @PreAuthorize hasRole('ADMIN')")
        void updateStatus_hasAdminPreAuthorize() throws NoSuchMethodException {
            Method method = ReturnRequestController.class.getDeclaredMethod(
                    "updateStatus", CustomUserDetails.class, UUID.class,
                    ReturnRequestStatus.class, String.class);

            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation).as("@PreAuthorize must be present on updateStatus").isNotNull();
            assertThat(annotation.value()).contains("ADMIN");
        }

        // AUTHZ-003 — customer endpoints must NOT have admin restriction
        @Test
        @DisplayName("AUTHZ-003: createReturn endpoint does NOT require ADMIN role (customer endpoint)")
        void createReturn_noAdminRestriction() throws NoSuchMethodException {
            Method method = ReturnRequestController.class.getDeclaredMethod(
                    "createReturn", CustomUserDetails.class, UUID.class,
                    backend.dto.returnrequest.CreateReturnRequest.class);

            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            // createReturn is a customer endpoint — no @PreAuthorize annotation
            // (auth is enforced by requiring a non-null principal + JWT filter)
            if (annotation != null) {
                assertThat(annotation.value()).doesNotContain("ADMIN");
            }
        }
    }

    // ── FileUploadController auth endpoints ────────────────────────────────────

    @Nested
    @DisplayName("FileUploadController — isAuthenticated() @PreAuthorize")
    class FileUploadAuth {

        // AUTHZ-004
        @Test
        @DisplayName("AUTHZ-004: uploadFile has @PreAuthorize isAuthenticated()")
        void uploadFile_hasIsAuthenticatedPreAuthorize() throws NoSuchMethodException {
            Method method = FileUploadController.class.getDeclaredMethod(
                    "uploadFile", MultipartFile.class, String.class);

            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation).as("@PreAuthorize must be present on uploadFile").isNotNull();
            assertThat(annotation.value()).contains("isAuthenticated()");
        }

        // AUTHZ-005
        @Test
        @DisplayName("AUTHZ-005: deleteFile has @PreAuthorize isAuthenticated()")
        void deleteFile_hasIsAuthenticatedPreAuthorize() throws NoSuchMethodException {
            Method method = FileUploadController.class.getDeclaredMethod(
                    "deleteFile", String.class);

            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation).as("@PreAuthorize must be present on deleteFile").isNotNull();
            assertThat(annotation.value()).contains("isAuthenticated()");
        }
    }

    // ── BannerController admin write endpoints ─────────────────────────────────

    @Nested
    @DisplayName("BannerController — write endpoints require ADMIN")
    class BannerControllerAuth {

        // AUTHZ-006 — spot-check: at least one write method on BannerController has ADMIN restriction
        @Test
        @DisplayName("AUTHZ-006: BannerController write methods annotated with hasRole('ADMIN')")
        void bannerController_writeMethods_haveAdminPreAuthorize() {
            long adminAnnotatedCount = java.util.Arrays.stream(BannerController.class.getDeclaredMethods())
                    .filter(m -> {
                        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                        return pa != null && pa.value().contains("ADMIN");
                    })
                    .count();

            assertThat(adminAnnotatedCount)
                    .as("BannerController should have at least one method annotated with ADMIN role")
                    .isGreaterThan(0);
        }
    }

    // ── Mixed-role write endpoints ─────────────────────────────────────────────

    @Nested
    @DisplayName("BrandController — write endpoints require ADMIN or SALES_STAFF")
    class BrandControllerAuth {

        // AUTHZ-007 — write endpoints on BrandController allow ADMIN or SALES_STAFF
        @Test
        @DisplayName("AUTHZ-007: BrandController write methods use hasAnyRole with ADMIN and SALES_STAFF")
        void brandController_writeMethods_haveAnyRolePreAuthorize() {
            long restrictedCount = java.util.Arrays.stream(BrandController.class.getDeclaredMethods())
                    .filter(m -> {
                        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                        return pa != null
                                && pa.value().contains("ADMIN")
                                && pa.value().contains("SALES_STAFF");
                    })
                    .count();

            assertThat(restrictedCount)
                    .as("BrandController should have write methods restricted to ADMIN and SALES_STAFF")
                    .isGreaterThan(0);
        }
    }
}
