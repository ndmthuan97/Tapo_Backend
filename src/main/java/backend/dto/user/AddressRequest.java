package backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,

        @NotBlank(message = "Địa chỉ không được để trống")
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @NotBlank(message = "Quận/huyện không được để trống")
        @Size(max = 100)
        String district,

        @NotBlank(message = "Tỉnh/thành phố không được để trống")
        @Size(max = 100)
        String city
) {}
