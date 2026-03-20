package backend.dto.user;

import backend.model.enums.UserRole;
import java.util.UUID;

public record UserDto(
    UUID id,
    String fullName,
    String email,
    String phoneNumber,
    String avatarUrl,
    UserRole role
) {}
