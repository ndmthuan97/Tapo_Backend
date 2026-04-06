package backend.controller;

import backend.dto.common.ApiResponse;
import backend.dto.common.CustomCode;
import backend.dto.user.*;
import backend.model.enums.UserRole;
import backend.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "User", description = "Quản lý tài khoản người dùng")
public class UserController {

    private final UserService userService;

    // ══════════════════════════════════════════════════════════════════════
    //  CUSTOMER — /api/users/me
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(Principal principal) {
        UserDto dto = userService.getMyProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(CustomCode.SUCCESS.getDefaultMessage(), dto));
    }

    @PatchMapping("/api/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserDto dto = userService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), dto));
    }

    @PatchMapping("/api/users/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), null));
    }

    // ── Addresses ─────────────────────────────────────────────────────────

    @GetMapping("/api/users/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getMyAddresses(Principal principal) {
        List<AddressDto> list = userService.getMyAddresses(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(CustomCode.SUCCESS.getDefaultMessage(), list));
    }

    @PostMapping("/api/users/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            Principal principal,
            @Valid @RequestBody AddressRequest request) {
        AddressDto dto = userService.addAddress(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.CREATED.getDefaultMessage(), dto));
    }

    @PutMapping("/api/users/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AddressDto>> updateAddress(
            Principal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        AddressDto dto = userService.updateAddress(principal.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), dto));
    }

    @DeleteMapping("/api/users/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            Principal principal,
            @PathVariable UUID id) {
        userService.deleteAddress(principal.getName(), id);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.DELETED.getDefaultMessage(), null));
    }

    @PatchMapping("/api/users/me/addresses/{id}/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setDefaultAddress(
            Principal principal,
            @PathVariable UUID id) {
        userService.setDefaultAddress(principal.getName(), id);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), null));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN — /api/admin/users
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserPageResponse>> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) UserRole role) {
        UserPageResponse result = userService.getAllUsers(page, size, role);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.SUCCESS.getDefaultMessage(), result));
    }

    @PatchMapping("/api/admin/users/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> lockUser(@PathVariable UUID id) {
        userService.lockUser(id);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), null));
    }

    @PatchMapping("/api/admin/users/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable UUID id) {
        userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.success(CustomCode.UPDATED.getDefaultMessage(), null));
    }
}
