package backend.dto.user;

import java.util.UUID;

public record AddressDto(
        UUID id,
        String recipientName,
        String phoneNumber,
        String address,
        String district,
        String city,
        Boolean isDefault
) {}
