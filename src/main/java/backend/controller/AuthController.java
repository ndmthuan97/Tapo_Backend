package backend.controller;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.ApiResponse;
import backend.dto.common.CustomCode;
import backend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success(CustomCode.CREATED.getDefaultMessage(), authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success(CustomCode.SUCCESS.getDefaultMessage(), authService.login(request)));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success(CustomCode.SUCCESS.getDefaultMessage(), authService.refreshToken(request)));
    }

    /**
     * Revoke the provided refresh token from Redis.
     * Client should include the refresh token in the request body.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        String rt = (request != null) ? request.refreshToken() : null;
        authService.logout(rt);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    /**
     * POST /api/auth/forgot-password
     * Body: { "email": "user@example.com" }
     * Always returns 200 regardless of whether email exists (security best practice).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại, một liên kết đặt lại mật khẩu đã được gửi.", null
        ));
    }

    /**
     * POST /api/auth/reset-password
     * Body: { "token": "uuid", "newPassword": "..." }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(
                "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập ngay.", null
        ));
    }

    // ── Inner request records ─────────────────────────────────────────────────

    record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6, max = 100) String newPassword
    ) {}
}
