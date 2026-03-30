package backend.dto.returnrequest;

import backend.model.enums.ReturnRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnRequestDto(
        UUID id,
        // Order info
        UUID orderId,
        String orderCode,
        // Requester
        UUID userId,
        String userName,
        // Return info
        String reason,
        List<String> evidenceImages,
        ReturnRequestStatus status,
        String staffNote,
        Instant createdAt
) {}
