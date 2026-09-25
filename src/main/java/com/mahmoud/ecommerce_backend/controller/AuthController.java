package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.auth.*;
import com.mahmoud.ecommerce_backend.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private static final long REFRESH_COOKIE_MAX_AGE = 604800L;

    private final AuthService authService;

    @Value("${app.cookie.same-site}")
    private String cookieSameSite;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthTokens tokens = authService.login(request);
        setRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(toAuthResponse(tokens));
    }


    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        String rawToken = readRefreshCookie(request);
        AuthTokens tokens = authService.refreshToken(rawToken);
        setRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(toAuthResponse(tokens));
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        String rawToken = readRefreshCookie(request);
        authService.logout(rawToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(
                AuthResponse.builder()
                        .message("Email verified successfully")
                        .build()
        );
    }


    /**
     * Public, unauthenticated. The response is deliberately identical whether
     * or not the email exists — no account enumeration. Rate-limited by
     * RateLimitFilter.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(
                AuthResponse.builder()
                        .message("If an account exists for that email, a password reset link has been sent.")
                        .build()
        );
    }


    /** Public, single-use, short-TTL token exchange for a new password. */
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(
                AuthResponse.builder()
                        .message("Password has been reset successfully. Please sign in.")
                        .build()
        );
    }


    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) REFRESH_COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);
    }


    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);
    }


    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }


    private AuthResponse toAuthResponse(AuthTokens tokens) {
        return AuthResponse.builder()
                .accessToken(tokens.accessToken())
                .userId(tokens.userId())
                .build();
    }
}
