package backend.service.impl;

import backend.constants.AppConstants;
import backend.dto.user.*;
import backend.exception.AuthException;
import backend.dto.common.CustomCode;
import backend.model.entity.Address;
import backend.model.entity.User;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.AddressRepository;
import backend.repository.UserRepository;
import backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────── Helpers ──────────────────────────────────

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );
    }

    private AddressDto toAddressDto(Address a) {
        return new AddressDto(
                a.getId(),
                a.getRecipientName(),
                a.getPhoneNumber(),
                a.getAddress(),
                a.getCity(),
                a.getIsDefault()
        );
    }

    // ─────────────────────────── Profile ──────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserDto getMyProfile(String email) {
        return toUserDto(findUserByEmail(email));
    }

    @Override
    @Transactional
    public UserDto updateProfile(String email, UpdateProfileRequest request) {
        User user = findUserByEmail(email);
        user.setFullName(request.fullName());
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            user.setAvatarUrl(request.avatarUrl());
        }
        return toUserDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AuthException(CustomCode.PASSWORD_MISMATCH);
        }
        User user = findUserByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException(CustomCode.PASSWORD_INCORRECT);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // ─────────────────────────── Addresses ────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AddressDto> getMyAddresses(String email) {
        User user = findUserByEmail(email);
        return addressRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::toAddressDto)
                .toList();
    }

    @Override
    @Transactional
    public AddressDto addAddress(String email, AddressRequest request) {
        User user = findUserByEmail(email);

        // If this is the first address, auto-set as default
        boolean hasExisting = !addressRepository.findAllByUserId(user.getId()).isEmpty();

        Address address = new Address();
        address.setUser(user);
        address.setRecipientName(request.recipientName());
        address.setPhoneNumber(request.phoneNumber());
        address.setAddress(request.address());
        address.setCity(request.city());
        address.setIsDefault(!hasExisting);

        return toAddressDto(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressDto updateAddress(String email, UUID addressId, AddressRequest request) {
        User user = findUserByEmail(email);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new AuthException(CustomCode.ADDRESS_NOT_FOUND));

        address.setRecipientName(request.recipientName());
        address.setPhoneNumber(request.phoneNumber());
        address.setAddress(request.address());
        address.setCity(request.city());

        return toAddressDto(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(String email, UUID addressId) {
        User user = findUserByEmail(email);
        Address address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new AuthException(CustomCode.ADDRESS_NOT_FOUND));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);

        // If the deleted address was default, promote the first remaining address
        if (wasDefault) {
            addressRepository.findAllByUserId(user.getId())
                    .stream()
                    .findFirst()
                    .ifPresent(first -> {
                        first.setIsDefault(true);
                        addressRepository.save(first);
                    });
        }
    }

    @Override
    @Transactional
    public void setDefaultAddress(String email, UUID addressId) {
        User user = findUserByEmail(email);
        Address target = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new AuthException(CustomCode.ADDRESS_NOT_FOUND));

        // Clear current default, then set the new one
        addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                .ifPresent(current -> {
                    current.setIsDefault(false);
                    addressRepository.save(current);
                });

        target.setIsDefault(true);
        addressRepository.save(target);
    }

    // ─────────────────────────── Admin ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse getAllUsers(int page, int size, UserRole role) {
        // Clamp page size to MAX_PAGE_SIZE
        int clampedSize = Math.min(size, AppConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page - 1, clampedSize, Sort.by("createdAt").descending());

        Page<User> result = (role != null)
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);

        List<UserDto> content = result.getContent().stream()
                .map(this::toUserDto)
                .toList();

        return new UserPageResponse(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                page,
                clampedSize
        );
    }

    @Override
    @Transactional
    public void lockUser(UUID userId) {
        User user = findUserById(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new AuthException(CustomCode.CANNOT_CHANGE_ADMIN_STATUS);
        }
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(UUID userId) {
        User user = findUserById(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new AuthException(CustomCode.CANNOT_CHANGE_ADMIN_STATUS);
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
}
