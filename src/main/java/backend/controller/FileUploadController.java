package backend.controller;

import backend.dto.common.ApiResponse;
import backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "API cho việc upload hình ảnh lên Supabase Storage")
public class FileUploadController {

    private static final Set<String> ALLOWED_PATHS =
            Set.of("general", "products", "banners", "avatars", "reviews");

    // Chỉ ADMIN, SALES_STAFF, WAREHOUSE_STAFF mới được upload vào các thư mục này
    private static final Set<String> STAFF_ONLY_PATHS = Set.of("products", "banners");

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload file", description = "Upload file (ảnh) lên Supabase Storage và trả về Public URL")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", required = false, defaultValue = "general") String path,
            Authentication authentication
    ) {
        if (!ALLOWED_PATHS.contains(path)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Đường dẫn không hợp lệ. Cho phép: " + ALLOWED_PATHS));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Chỉ chấp nhận file ảnh (image/*)"));
        }

        if (STAFF_ONLY_PATHS.contains(path)) {
            boolean isStaff = authentication.getAuthorities().stream()
                    .anyMatch(a -> {
                        String role = a.getAuthority();
                        return role.equals("ROLE_ADMIN")
                            || role.equals("ROLE_SALES_STAFF")
                            || role.equals("ROLE_WAREHOUSE_STAFF");
                    });
            if (!isStaff) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error(403, "Không có quyền upload vào thư mục này"));
            }
        }

        String url = fileStorageService.uploadFile(file, path);
        return ResponseEntity.ok(ApiResponse.success("Upload file thành công", url));
    }

    @DeleteMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete file", description = "Xóa file khỏi Supabase Storage dựa trên Public URL")
    public ResponseEntity<ApiResponse<Boolean>> deleteFile(
            @RequestParam("fileUrl") String fileUrl
    ) {
        boolean result = fileStorageService.deleteFile(fileUrl);
        if (result) {
            return ResponseEntity.ok(ApiResponse.success("Xóa file thành công", true));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể xóa file hoặc URL không hợp lệ"));
        }
    }
}
