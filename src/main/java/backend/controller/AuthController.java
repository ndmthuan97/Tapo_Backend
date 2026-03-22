package backend.controller;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.ApiResponse;
import backend.dto.common.CustomCode;
import backend.service.AuthService;
import jakarta.validation.Valid;
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
}
