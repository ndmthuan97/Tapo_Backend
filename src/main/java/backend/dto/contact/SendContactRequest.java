package backend.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for receiving contact form submission from frontend.
 * Uses Java 21 record — immutable, auto-equals/hashCode/toString.
 */
public record SendContactRequest(

        @NotBlank(message = "Tên không được để trống")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        String email,

        @Size(max = 20)
        String phone,

        @NotBlank(message = "Chủ đề không được để trống")
        @Size(max = 100)
        String topic,

        @NotBlank(message = "Nội dung không được để trống")
        @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự")
        String message
) {}
