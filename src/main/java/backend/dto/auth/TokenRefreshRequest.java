package backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
    @NotBlank(message = "Hãy cung cấp Access Token")
    String accessToken,

    @NotBlank(message = "Hãy cung cấp Refresh Token")
    String refreshToken
) {}
