package com.mahmoud.ecommerce_backend.service.auth;

import com.mahmoud.ecommerce_backend.dto.auth.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.enums.UserStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    // ===================== REGISTER =====================
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        User user = User.builder()
                .firstName(request.getName())
                .lastName("")
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING_VERIFICATION)
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

        return generateTokens(user, null);
    }

    // ===================== LOGIN =====================
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return generateTokens(user, authentication);
    }

    // ===================== TOKEN GENERATION =====================
    private AuthResponse generateTokens(User user, Authentication authentication) {

        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {

            List<SimpleGrantedAuthority> authorities = userRoleRepository
                    .findByUser(user)
                    .stream()
                    .map(ur -> new SimpleGrantedAuthority(
                            ur.getRole().getName().name()
                    ))
                    .toList();

            authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    null,
                    authorities
            );
        }

        String accessToken = jwtUtils.generateToken(authentication);

        // 🔥 NEW refresh token
        String newRefreshToken = UUID.randomUUID().toString();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshToken) // ❗ NO hashing (fast lookup)
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    // ===================== REFRESH =====================
    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {

        RefreshToken token = refreshTokenRepository.findByTokenHash(refreshToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid refresh token"));

        if (!token.isValid()) {
            throw new BadRequestException("Refresh token expired or revoked");
        }

        User user = token.getUser();

        // 🔥 TOKEN ROTATION
        token.setRevoked(true);
        token.setRevokedAt(Instant.now());

        return generateTokens(user, null);
    }

    // ===================== LOGOUT =====================
    @Override
    @Transactional
    public void logout(String refreshToken) {

        RefreshToken token = refreshTokenRepository.findByTokenHash(refreshToken)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());
    }
}