package backend.dto.returnrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReturnRequest(
        @NotBlank(message = "Lý do hoàn trả không được để trống")
        @Size(max = 1000)
        String reason,

        List<String> evidenceImages
) {}
