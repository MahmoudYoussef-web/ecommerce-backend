package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 regression: the refresh endpoint must re-apply the same user-state
 * gate as login. A user disabled, locked, or unverified AFTER a refresh
 * cookie was issued must not be able to mint new tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RefreshTokenLifecycleRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private com.mahmoud.ecommerce_backend.security.config.RateLimitFilter rateLimitFilter;

    private static final String EMAIL = "user@gmail.com";

    @org.junit.jupiter.api.BeforeEach
    void resetRateLimitBucketsAndUserState() {
        // Other test classes in the same shared Spring context drain the
        // refresh bucket; tests here must not inherit their throttling.
        Object buckets = org.springframework.test.util.ReflectionTestUtils.getField(rateLimitFilter, "buckets");
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) buckets).invalidateAll();

        // Defensive: a prior crashed run may have left the seeded user locked.
        jdbcTemplate.update(
                "UPDATE users SET enabled = 1, account_non_locked = 1, email_verified = 1, status = 'ACTIVE' WHERE email = ?",
                EMAIL);
    }

    @Test
    void activeUserCanRefresh() {
        String cookie = loginAndCaptureCookie();

        ResponseEntity<Map> refresh = refreshWithCookie(cookie);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) refresh.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    void disabledUserIsRejectedOnRefresh() {
        String cookie = loginAndCaptureCookie();
        try {
            jdbcTemplate.update("UPDATE users SET enabled = 0 WHERE email = ?", EMAIL);

            ResponseEntity<Map> refresh = refreshWithCookie(cookie);
            assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbcTemplate.update("UPDATE users SET enabled = 1 WHERE email = ?", EMAIL);
        }
    }

    @Test
    void lockedUserIsRejectedOnRefresh() {
        String cookie = loginAndCaptureCookie();
        try {
            jdbcTemplate.update("UPDATE users SET account_non_locked = 0 WHERE email = ?", EMAIL);

            ResponseEntity<Map> refresh = refreshWithCookie(cookie);
            assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbcTemplate.update("UPDATE users SET account_non_locked = 1 WHERE email = ?", EMAIL);
        }
    }

    @Test
    void unverifiedUserIsRejectedOnRefresh() {
        String cookie = loginAndCaptureCookie();
        try {
            jdbcTemplate.update(
                    "UPDATE users SET email_verified = 0, status = 'PENDING_VERIFICATION' WHERE email = ?",
                    EMAIL);

            ResponseEntity<Map> refresh = refreshWithCookie(cookie);
            assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbcTemplate.update(
                    "UPDATE users SET email_verified = 1, status = 'ACTIVE' WHERE email = ?",
                    EMAIL);
        }
    }

    @Test
    void revokedOrInvalidRefreshTokenIsRejected() {
        HttpHeaders headers = refreshHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=not-a-real-token");

        ResponseEntity<Map> refresh = restTemplate.exchange(
                baseUrl() + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        assertThat(refresh.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void reusedRotatedTokenIsRejected() {
        String cookie = loginAndCaptureCookie();

        ResponseEntity<Map> first = refreshWithCookie(cookie);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The old cookie was revoked by rotation — replaying it must fail.
        ResponseEntity<Map> replay = refreshWithCookie(cookie);
        assertThat(replay.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void tokenVersionBumpInvalidatesOutstandingAccessToken() {
        String cookie = loginAndCaptureCookie();
        ResponseEntity<Map> refresh = refreshWithCookie(cookie);
        String accessToken = (String) refresh.getBody().get("accessToken");

        // Sanity: fresh token works.
        ResponseEntity<Map> me = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(accessToken)),
                Map.class
        );
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Bump token_version: outstanding access tokens must die.
        jdbcTemplate.update("UPDATE users SET token_version = token_version + 1 WHERE email = ?", EMAIL);

        ResponseEntity<Map> after = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(accessToken)),
                Map.class
        );
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- helpers ----------

    private String loginAndCaptureCookie() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("123456");

        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", request, Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        return login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .map(c -> c.split(";")[0])
                .orElseThrow(() -> new AssertionError("refresh cookie missing"));
    }

    private ResponseEntity<Map> refreshWithCookie(String cookie) {
        HttpHeaders headers = refreshHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return restTemplate.exchange(
                baseUrl() + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );
    }

    private HttpHeaders refreshHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
