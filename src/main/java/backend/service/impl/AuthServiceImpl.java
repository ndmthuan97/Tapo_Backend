package backend.service.impl;

import backend.dto.auth.AuthResponse;
import backend.dto.auth.LoginRequest;
import backend.dto.auth.RegisterRequest;
import backend.dto.auth.TokenRefreshRequest;
import backend.dto.common.CustomCode;
import backend.dto.user.UserDto;
import backend.exception.AuthException;
import backend.model.entity.User;
import backend.model.enums.UserRole;
import backend.model.enums.UserStatus;
import backend.repository.UserRepository;
import backend.constants.AppConstants;
import backend.security.JwtTokenProvider;
import backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(CustomCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setAvatarUrl(AppConstants.DEFAULT_AVATAR);

        User savedUser = userRepository.save(user);
        return generateAuthResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AuthException(CustomCode.ACCOUNT_LOCKED);
            }

            return generateAuthResponse(user);
        } catch (AuthenticationException ex) {
            throw new AuthException(CustomCode.CREDENTIALS_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();

        if (tokenProvider.validateToken(refreshToken)) {
            String email = tokenProvider.getEmailFromToken(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException(CustomCode.INVALID_REFRESH_TOKEN));

            return generateAuthResponse(user);
        }

        throw new AuthException(CustomCode.EXPIRED_REFRESH_TOKEN);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        UserDto userDto = new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );

        return new AuthResponse(accessToken, refreshToken, userDto);
    }
}
