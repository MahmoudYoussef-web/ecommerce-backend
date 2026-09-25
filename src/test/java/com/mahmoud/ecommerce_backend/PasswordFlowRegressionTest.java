package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.ForgotPasswordRequest;
import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.auth.RegisterRequest;
import com.mahmoud.ecommerce_backend.dto.auth.ResetPasswordRequest;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.service.common.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: password change + forgot/reset lifecycle.
 *
 * EmailService is replaced with a capturing fake so tests can obtain the raw
 * reset token exactly as a real user would (from the email link) while the
 * production path keeps persisting only its SHA-256 hash.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordFlowRegressionTest {

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        EmailService capturingEmailService() {
            return new EmailService() {

                final List<String[]> sent = new ArrayList<>();

                @Override
                public void send(String to, String subject, String body) {
                    sent.add(new String[]{to, subject, body});
                }

                @Override
                public void sendEmailVerification(String to, String token) {
                    send(to, "verify", "http://frontend/verify-email?token=" + token);
                }

                @Override
                public void sendPasswordReset(String to, String token) {
                    // Mirror production body shape so the token is extractable
                    // the same way a mail client user would copy it.
                    send(to, "Reset your password",
                            "http://frontend/reset-password?token=" + token);
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private EmailService emailService;

    private List<String[]> capturedMails() {
        return (List<String[]>) org.springframework.test.util.ReflectionTestUtils
                .getField(emailService, "sent");
    }

    @BeforeEach
    void resetRateLimitBuckets() {
        Object buckets = org.springframework.test.util.ReflectionTestUtils.getField(rateLimitFilter, "buckets");
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) buckets).invalidateAll();
    }

    @Test
    void passwordChangeRequiresCorrectCurrentPassword() {
        String email = registerUser();

        LoginRequest loginReq = login(email, "Password123!");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", loginReq, Map.class);
        String accessToken = (String) login.getBody().get("accessToken");

        Map<String, Object> badChange = Map.of(
                "currentPassword", "WrongCurrent1!",
                "newPassword", "NewPassword456!"
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/users/me/password",
                HttpMethod.POST,
                new HttpEntity<>(badChange, bearer(accessToken)),
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Old password must still work after the failed attempt.
        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", loginReq, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void passwordChangeInvalidatesAccessAndRefreshSessions() {
        String email = registerUser();
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", login(email, "Password123!"), Map.class);
        String oldAccessToken = (String) login.getBody().get("accessToken");
        String oldCookie = cookieFrom(login);

        try {
            Map<String, Object> change = Map.of(
                    "currentPassword", "Password123!",
                    "newPassword", "NewPassword456!"
            );
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/api/users/me/password",
                    HttpMethod.POST,
                    new HttpEntity<>(change, bearer(oldAccessToken)),
                    Map.class
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Outstanding access token is dead (tokenVersion bumped).
            assertThat(restTemplate.exchange(
                    baseUrl() + "/api/users/me",
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(oldAccessToken)),
                    Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // Outstanding refresh session cannot mint new tokens.
            HttpHeaders refreshHeaders = new HttpHeaders();
            refreshHeaders.setOrigin("http://localhost:5173");
            refreshHeaders.add(HttpHeaders.COOKIE, oldCookie);
            assertThat(restTemplate.exchange(
                    baseUrl() + "/api/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(refreshHeaders),
                    Map.class).getStatusCode().is4xxClientError()).isTrue();

            // Old password fails, new password works.
            assertThat(restTemplate.postForEntity(
                    baseUrl() + "/api/auth/login", login(email, "Password123!"), Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(restTemplate.postForEntity(
                    baseUrl() + "/api/auth/login", login(email, "NewPassword456!"), Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE email = ?", originalHash, email);
        }
    }

    @Test
    void forgotPasswordDoesNotRevealAccountExistence() {
        registerUser();

        ForgotPasswordRequest known = ForgotPasswordRequest.builder()
                .email(registerEmail).build();
        ForgotPasswordRequest unknown = ForgotPasswordRequest.builder()
                .email("nobody-" + UUID.randomUUID() + "@test.com").build();

        ResponseEntity<Map> knownResp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/forgot-password", known, Map.class);
        ResponseEntity<Map> unknownResp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/forgot-password", unknown, Map.class);

        assertThat(knownResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknownResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(unknownResp.getBody()))
                .isEqualTo(String.valueOf(knownResp.getBody()));
    }

    @Test
    void resetTokenIsHashedOnlySingleUseAndExpiring() throws Exception {
        registerUser();

        requestResetAndAwaitMail();

        // Only the hash may be persisted: no row stores the raw token value.
        String rawToken = latestResetToken();
        Integer rawHits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class, rawToken);
        assertThat(rawHits).isZero();

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM password_reset_tokens ORDER BY id DESC LIMIT 1",
                String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(rawToken);
    }

    @Test
    void fullResetFlowChangesPasswordAndKillsOldSessions() {
        String email = registerUser();

        // Pre-existing session issued before the reset.
        ResponseEntity<Map> preLogin = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", login(email, "Password123!"), Map.class);
        String staleAccessToken = (String) preLogin.getBody().get("accessToken");

        requestResetAndAwaitMail();
        String token = latestResetToken();

        Map<String, Object> body = Map.of("token", token, "newPassword", "BrandNew789!");
        ResponseEntity<Map> reset = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password", body, Map.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Single use.
        ResponseEntity<Map> replay = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password", body, Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Invalid token shape → generic rejection.
        ResponseEntity<Map> garbage = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password",
                Map.of("token", "garbage-token", "newPassword", "Whatever123!"),
                Map.class);
        assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Stale access token invalidated by the reset.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(staleAccessToken)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Old password fails; new password logs in successfully.
        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", login(email, "Password123!"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", login(email, "BrandNew789!"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredResetTokenIsRejected() {
        registerUser();
        requestResetAndAwaitMail();
        String token = latestResetToken();

        // Expire via the JDBC layer with an explicit Timestamp so the value
        // travels the same timezone path Hibernate uses for DATETIME(6)
        // (a raw NOW()-based UPDATE would be written in server-local time).
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET expires_at = ? ORDER BY id DESC LIMIT 1",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(120)));

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password",
                Map.of("token", token, "newPassword", "Whatever123!"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- helpers ----------

    private String registerEmail;

    private String registerUser() {
        registerEmail = "pwdflow-" + UUID.randomUUID() + "@test.com";
        RegisterRequest req = RegisterRequest.builder()
                .firstName("Pwd").lastName("Flow")
                .email(registerEmail)
                .password("Password123!")
                .build();
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return registerEmail;
    }

    /** The fake EmailService bean captures outgoing mail; read it via the proxy. */
    private void requestResetAndAwaitMail() {
        int before = capturedMails().size();
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/forgot-password",
                ForgotPasswordRequest.builder().email(registerEmail).build(),
                Map.class);
        long deadline = System.currentTimeMillis() + 5000;
        while (capturedMails().size() <= before && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        assertThat(capturedMails().size()).isGreaterThan(before);
    }

    private String latestResetToken() {
        for (int i = capturedMails().size() - 1; i >= 0; i--) {
            String[] mail = capturedMails().get(i);
            if (mail[2].contains("/reset-password?token=")) {
                return mail[2].split("token=")[1].trim();
            }
        }
        throw new AssertionError("no reset mail captured");
    }

    private LoginRequest login(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private String cookieFrom(ResponseEntity<Map> login) {
        return login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .map(c -> c.split(";")[0])
                .orElseThrow(() -> new AssertionError("refresh cookie missing"));
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
