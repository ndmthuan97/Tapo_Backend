package backend.service;

import backend.dto.user.*;
import backend.model.enums.UserRole;

import java.util.List;
import java.util.UUID;

public interface UserService {

    // ── Profile ────────────────────────────────────────────────────────────
    UserDto getMyProfile(String email);
    UserDto updateProfile(String email, UpdateProfileRequest request);
    void changePassword(String email, ChangePasswordRequest request);

    // ── Addresses ──────────────────────────────────────────────────────────
    List<AddressDto> getMyAddresses(String email);
    AddressDto addAddress(String email, AddressRequest request);
    AddressDto updateAddress(String email, UUID addressId, AddressRequest request);
    void deleteAddress(String email, UUID addressId);
    void setDefaultAddress(String email, UUID addressId);

    // ── Admin ──────────────────────────────────────────────────────────────
    UserPageResponse getAllUsers(int page, int size, UserRole role);
    void lockUser(UUID userId);
    void unlockUser(UUID userId);
}
