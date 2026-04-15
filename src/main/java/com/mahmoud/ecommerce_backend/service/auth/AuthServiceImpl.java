package com.mahmoud.ecommerce_backend.service.auth;

import com.mahmoud.ecommerce_backend.dto.auth.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.enums.UserStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import com.mahmoud.ecommerce_backend.service.common.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;


    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .verificationToken(verificationToken)
                .accountNonLocked(true)
                .enabled(true)
                .tenantId(1L)
                .build();

        userRepository.save(user);

        Role role = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        userRoleRepository.save(
                UserRole.builder()
                        .user(user)
                        .role(role)
                        .build()
        );

        emailService.sendEmailVerification(user.getEmail(), verificationToken);

        return AuthResponse.builder()
                .message("User registered successfully. Please verify your email.")
                .build();
    }


    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateUser(user);

        return generateTokens(user);
    }


    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {

        String rawToken = request.getRefreshToken();

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token required");
        }

        List<RefreshToken> tokens =
                refreshTokenRepository.findByRevokedFalseAndExpiresAtAfter(Instant.now());

        RefreshToken token = tokens.stream()
                .filter(t -> passwordEncoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Invalid refresh token"));

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());

        return generateTokens(token.getUser());
    }


    @Override
    @Transactional
    public void logout(LogoutRequest request) {

        String rawToken = request.getRefreshToken();

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token required");
        }

        List<RefreshToken> tokens =
                refreshTokenRepository.findByRevokedFalse();

        RefreshToken token = tokens.stream()
                .filter(t -> passwordEncoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());
    }


    private AuthResponse generateTokens(User user) {

        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        String accessToken = jwtUtils.generateToken(
                user.getId(),
                user.getEmail(),
                roles,
                user.getTokenVersion(),
                user.getTenantId()
        );

        String rawRefreshToken = UUID.randomUUID().toString();
        String hashedToken = passwordEncoder.encode(rawRefreshToken);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hashedToken)
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }



    private void validateUser(User user) {

        if (!user.isEmailVerified()) {
            throw new BadRequestException("Email not verified");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Account not active");
        }

        if (!user.isEnabled()) {
            throw new BadRequestException("Account disabled");
        }

        if (!user.isAccountNonLocked()) {
            throw new BadRequestException("Account locked");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }
}