package backend.dto.user;

import java.util.UUID;

/**
 * Địa chỉ giao hàng — không còn trường district sau sát nhập hành chính VN.
 * Cấu trúc: recipientName · phoneNumber · address (số nhà + đường + phường/xã) · city (tỉnh/TP)
 */
public record AddressDto(
        UUID id,
        String recipientName,
        String phoneNumber,
        String address,
        String city,
        Boolean isDefault
) {}
