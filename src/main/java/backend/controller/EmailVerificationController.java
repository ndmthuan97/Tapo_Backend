package backend.controller;

import backend.dto.common.ApiResponse;
import backend.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * GET /api/auth/verify-email?token=xxx
     * Called when user clicks the link in their email.
     * Public endpoint — no auth required.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(
                "Tài khoản đã được xác thực thành công! Bạn có thể đăng nhập ngay.",
                null
        ));
    }

    /**
     * POST /api/auth/resend-verification
     * Body: { "email": "user@example.com" }
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody ResendRequest req) {
        emailVerificationService.resendVerification(req.email());
        return ResponseEntity.ok(ApiResponse.success(
                "Email xác thực đã được gửi lại. Vui lòng kiểm tra hộp thư của bạn.",
                null
        ));
    }

    record ResendRequest(String email) {}
}
