package backend.controller;

import backend.service.FileStorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileUploadController Unit Tests — path whitelist validation.
 *
 * Test cases: FILE-002, FILE-003, FILE-004, FILE-006
 * FILE-001 (real Supabase upload) and FILE-005 (real Supabase delete) are MANUAL.
 *
 * Note: @PreAuthorize("isAuthenticated()") is NOT enforced in standaloneSetup.
 * Production auth is validated via JWT filter (integration concern).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadController — Unit Tests")
class FileUploadControllerTest {

    @Mock FileStorageService fileStorageService;

    @InjectMocks FileUploadController fileUploadController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(fileUploadController)
                .build();
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "test.jpg", "image/jpeg",
                "fake-image-bytes".getBytes());
    }

    // ── POST /api/files/upload ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/files/upload")
    class UploadFile {

        @Test
        @DisplayName("upload to valid path 'products' → 200 with URL (FILE-002 valid-auth happy path)")
        void upload_validPath_200() throws Exception {
            given(fileStorageService.uploadFile(any(), eq("products")))
                    .willReturn("https://storage.example.com/products/test.jpg");

            mockMvc.perform(multipart("/api/files/upload")
                            .file(imageFile())
                            .param("path", "products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("https://storage.example.com/products/test.jpg"))
                    .andExpect(jsonPath("$.message").value("Upload file thành công"));
        }

        @Test
        @DisplayName("upload with default path omitted → uses 'general' → 200")
        void upload_defaultPath_200() throws Exception {
            given(fileStorageService.uploadFile(any(), eq("general")))
                    .willReturn("https://storage.example.com/general/test.jpg");

            mockMvc.perform(multipart("/api/files/upload")
                            .file(imageFile()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Upload file thành công"));
        }

        // FILE-003 — path traversal attempt → 400
        @Test
        @DisplayName("FILE-003: upload with path='../../etc' → 400 Đường dẫn không hợp lệ")
        void upload_pathTraversal_400() throws Exception {
            mockMvc.perform(multipart("/api/files/upload")
                            .file(imageFile())
                            .param("path", "../../etc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Đường dẫn không hợp lệ")));
        }

        // FILE-004 — arbitrary invalid path → 400
        @Test
        @DisplayName("FILE-004: upload with path='hack' → 400 Đường dẫn không hợp lệ")
        void upload_invalidPath_400() throws Exception {
            mockMvc.perform(multipart("/api/files/upload")
                            .file(imageFile())
                            .param("path", "hack"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Đường dẫn không hợp lệ")));
        }

        @Test
        @DisplayName("upload with path='reviews' (allowed) → 200")
        void upload_reviewsPath_200() throws Exception {
            given(fileStorageService.uploadFile(any(), eq("reviews")))
                    .willReturn("https://storage.example.com/reviews/test.jpg");

            mockMvc.perform(multipart("/api/files/upload")
                            .file(imageFile())
                            .param("path", "reviews"))
                    .andExpect(status().isOk());
        }
    }

    // ── DELETE /api/files/delete ──────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/files/delete")
    class DeleteFile {

        // FILE-006 — deleteFile returns false → 400
        @Test
        @DisplayName("FILE-006: deleteFile with non-existent URL → false → 400")
        void deleteFile_invalidUrl_400() throws Exception {
            given(fileStorageService.deleteFile("https://invalid-url.com/file.jpg"))
                    .willReturn(false);

            mockMvc.perform(delete("/api/files/delete")
                            .param("fileUrl", "https://invalid-url.com/file.jpg"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("deleteFile with valid URL → true → 200")
        void deleteFile_validUrl_200() throws Exception {
            given(fileStorageService.deleteFile("https://storage.example.com/products/test.jpg"))
                    .willReturn(true);

            mockMvc.perform(delete("/api/files/delete")
                            .param("fileUrl", "https://storage.example.com/products/test.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true))
                    .andExpect(jsonPath("$.message").value("Xóa file thành công"));
        }
    }
}
