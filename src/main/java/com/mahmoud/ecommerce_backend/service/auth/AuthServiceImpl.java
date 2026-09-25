package com.mahmoud.ecommerce_backend.service.auth;

import com.mahmoud.ecommerce_backend.dto.auth.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.enums.UserStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import com.mahmoud.ecommerce_backend.service.common.email.EmailService;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TTL_SECONDS = 604800L;

    private static final long VERIFICATION_TOKEN_TTL_SECONDS = 86400L;

    /** Reset links live 30 minutes — short enough to limit a leaked link. */
    private static final long RESET_TOKEN_TTL_SECONDS = 1800L;

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecurityService securityService;

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

        // Re-apply the same gate as login: a user disabled, locked, suspended
        // or unverified after the cookie was issued must not mint new tokens.
        validateUser(token.getUser());

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

        // NOTE(Phase 4): the verification token is deliberately RETAINED after
        // successful verification. Re-verification stays an idempotent no-op
        // (Phase 1/2 regression contract); only its expiry invalidates it.
    }


    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {

        User user = securityService.getCurrentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())
                || request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new BadRequestException("New password must differ from the current password");
        }

        applyPasswordChange(user, request.getNewPassword());
    }


    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {

        String email = normalizeEmail(request.getEmail());

        userRepository.findByEmail(email).ifPresent(user -> {

            // Accounts that cannot log in (disabled/locked/unverified) gain
            // nothing from a reset link; skip silently to keep the public
            // response identical either way.
            try {
                validateUser(user);
            } catch (BadRequestException ex) {
                log.info("Password reset requested for non-active account | reason={}", ex.getMessage());
                return;
            }

            String rawToken = generateResetToken();
            PasswordResetToken record = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256Hex(rawToken))
                    .expiresAt(Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS))
                    .build();

            passwordResetTokenRepository.save(record);

            // The raw token exists only in the emailed link: never logged,
            // never persisted, never returned by any API response.
            emailService.sendPasswordReset(user.getEmail(), rawToken);
        });
    }


    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        String hash = sha256Hex(request.getToken());

        PasswordResetToken record = passwordResetTokenRepository
                .findByTokenHashAndConsumedFalse(hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (record.isExpired()) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = record.getUser();

        validateUser(user);

        applyPasswordChange(user, request.getNewPassword());

        record.consume();
    }


    /**
     * Shared tail of every password mutation: store the BCrypt hash, bump the
     * tokenVersion so all outstanding ACCESS tokens fail the AuthTokenFilter
     * check immediately, and revoke every active refresh session so no cookie
     * can mint a fresh access token afterwards.
     */
    private void applyPasswordChange(User user, String rawNewPassword) {

        user.setPasswordHash(passwordEncoder.encode(rawNewPassword));

        user.incrementTokenVersion();

        List<RefreshToken> activeSessions =
                refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());

        activeSessions.forEach(session -> session.revoke(null));

        log.info("Password changed and sessions invalidated | userId={} revokedSessions={}",
                user.getId(), activeSessions.size());
    }


    /** 256 bits of CSPRNG output, URL-safe. Entropy is the only secret. */
    private static String generateResetToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
        String hashedToken = sha256Hex(rawRefreshToken);

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

        String hash = sha256Hex(rawToken);

        return (checkExpiry
                ? refreshTokenRepository.findByTokenHashAndRevokedFalseAndExpiresAtAfter(hash, Instant.now())
                : refreshTokenRepository.findByTokenHashAndRevokedFalse(hash))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid refresh token"));
    }


    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
