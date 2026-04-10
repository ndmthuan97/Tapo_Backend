package backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for sending a chat message.
 */
public record SendMessageRequest(
        @NotBlank(message = "Nội dung không được để trống")
        @Size(max = 2000, message = "Nội dung không quá 2000 ký tự")
        String content
) {}
