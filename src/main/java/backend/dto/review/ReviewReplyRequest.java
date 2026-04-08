package backend.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for admin reply to a review.
 */
public record ReviewReplyRequest(
        @NotBlank(message = "Nội dung phản hồi không được để trống")
        @Size(max = 2000, message = "Phản hồi tối đa 2000 ký tự")
        String reply
) {}
