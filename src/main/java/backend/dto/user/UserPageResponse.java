package backend.dto.user;

import java.util.List;

public record UserPageResponse(
        List<UserDto> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
