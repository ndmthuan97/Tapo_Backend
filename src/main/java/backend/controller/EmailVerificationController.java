package backend.controller;

import backend.dto.common.ApiResponse;
import backend.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Xác thực & phân quyền người dùng")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping("/verify-email")
    @Operation(
            summary = "Xác thực email",
            description = "Endpoint công khai — người dùng nhấn link trong email để xác thực tài khoản."
    )
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Parameter(description = "Token xác thực được gửi qua email", required = true)
            @RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(
                "Tài khoản đã được xác thực thành công! Bạn có thể đăng nhập ngay.",
                null
        ));
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Gửi lại email xác thực",
            description = "Gửi lại link xác thực đến email đã đăng ký. Dùng khi email cũ đã hết hạn."
    )
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "Email xác thực đã được gửi lại. Vui lòng kiểm tra hộp thư của bạn.",
                null
        ));
    }

    /** Request body cho endpoint gửi lại email xác thực */
    record ResendVerificationRequest(String email) {}
}
