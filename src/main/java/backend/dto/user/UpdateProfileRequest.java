package backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,

        @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber
) {}
