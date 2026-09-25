package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: the server must never accept unsigned, wrong-algorithm,
 * foreign-key or expired JWTs regardless of header claims.
 */
class JwtAlgorithmHardeningTest {

    private static final String SECRET_B64 =
            Base64.getEncoder().encodeToString(
                    "phase4-hardening-test-secret-0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private JwtUtils jwtUtils;

    @BeforeEach
    void init() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET_B64);
        ReflectionTestUtils.setField(jwtUtils, "expiration", 600_000L);
    }

    private Key attackerKey() {
        return Keys.hmacShaKeyFor(("attacker-attempts-" + SECRET_B64).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void legitimatelyIssuedTokenIsValidates() {
        String token = jwtUtils.generateToken(1L, "a@b.c", List.of("ROLE_CUSTOMER"), 0, 1L);
        assertThat(jwtUtils.validate(token)).isTrue();
        assertThat(jwtUtils.extractUserId(token)).isEqualTo(1L);
        assertThat(jwtUtils.extractTokenVersion(token)).isZero();
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        // Header {"alg":"none"}, no signature segment.
        String unsigned = Jwts.builder()
                .setSubject("a@b.c")
                .claim("uid", 1L)
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .compact();

        assertThat(unsigned.split("\\.")).hasSize(2);
        assertThat(jwtUtils.validate(unsigned)).isFalse();
    }

    @Test
    void tokenSignedWithForeignHmacSecretIsRejected() {
        String foreign = Jwts.builder()
                .setSubject("a@b.c")
                .claim("uid", 1L)
                .claim("roles", List.of("ROLE_ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(attackerKey(), SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtils.validate(foreign)).isFalse();
    }

    @Test
    void expiredAccessTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtils, "expiration", -1_000L);

        String expired = jwtUtils.generateToken(1L, "a@b.c", List.of("ROLE_CUSTOMER"), 0, 1L);

        assertThat(jwtUtils.validate(expired)).isFalse();
    }
}
