package com.mahmoud.ecommerce_backend.dto.auth;

/**
 * Internal auth result carrying the raw refresh token so the controller can
 * place it in an httpOnly cookie. The refresh token is never serialized to JSON.
 */
public record AuthTokens(String accessToken, String refreshToken, Long userId) {
}
