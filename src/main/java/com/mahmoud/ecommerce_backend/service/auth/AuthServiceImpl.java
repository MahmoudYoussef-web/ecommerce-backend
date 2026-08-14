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
import org.springframework.beans.factory.annotation.Value;
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

    private static final long REFRESH_TTL_SECONDS = 604800L;

    private static final long VERIFICATION_TOKEN_TTL_SECONDS = 86400L;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.dev.auto-verify-email}")
    private boolean autoVerifyEmail;

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
                .status(autoVerifyEmail ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION)
                .emailVerified(autoVerifyEmail)
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_SECONDS))
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
    public AuthTokens login(LoginRequest request) {

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
    public AuthTokens refreshToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh token required");
        }

        RefreshToken token = findToken(rawToken, true);

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());

        return generateTokens(token.getUser());
    }


    @Override
    @Transactional
    public void logout(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        RefreshToken token = findToken(rawToken, false);

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());
    }


    @Override
    @Transactional
    public void verifyEmail(String token) {

        if (token == null || token.isBlank()) {
            throw new BadRequestException("Verification token required");
        }

        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token"));

        if (user.getVerificationTokenExpiresAt() != null
                && user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Invalid or expired verification token");
        }

        if (user.isEmailVerified()) {
            return;
        }

        user.verifyEmail();
        user.activate();
    }


    private AuthTokens generateTokens(User user) {

        List<String> roles = userRoleRepository.findByUserIdWithRoles(user.getId())
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
                .expiresAt(Instant.now().plusSeconds(REFRESH_TTL_SECONDS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);

        return new AuthTokens(accessToken, rawRefreshToken, user.getId());
    }


    private RefreshToken findToken(String rawToken, boolean checkExpiry) {

        List<RefreshToken> tokens = checkExpiry
                ? refreshTokenRepository.findByRevokedFalseAndExpiresAtAfter(Instant.now())
                : refreshTokenRepository.findByRevokedFalse();

        return tokens.stream()
                .filter(t -> passwordEncoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Invalid refresh token"));
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
