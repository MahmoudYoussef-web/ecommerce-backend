package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that access tokens signed with any other HMAC secret are rejected.
 * Uses two freshly generated throwaway keys; no real credential is embedded.
 */
class ForeignSecretJwtTest {

    private static final String KEY_A =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
    private static final String KEY_B =
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB==";

    private static final long TTL_MS = 3_600_000L;

    private JwtUtils jwtUtilsWithSecret(String secret) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "expiration", TTL_MS);
        return utils;
    }

    private String tokenFrom(JwtUtils utils) {
        return utils.generateToken(1L, "user@example.com", List.of("ROLE_CUSTOMER"), 0, 1L);
    }

    @Test
    void tokenSignedWithForeignSecretIsRejected() {
        JwtUtils issuer = jwtUtilsWithSecret(KEY_A);
        JwtUtils verifier = jwtUtilsWithSecret(KEY_B);

        String token = tokenFrom(issuer);

        assertTrue(issuer.validate(token), "same-secret validation must succeed");
        assertFalse(verifier.validate(token), "token signed with a different secret must be rejected");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtils issuer = jwtUtilsWithSecret(KEY_A);
        JwtUtils verifier = jwtUtilsWithSecret(KEY_A);

        String token = tokenFrom(issuer);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertFalse(verifier.validate(tampered), "tampered token must be rejected");
    }
}
