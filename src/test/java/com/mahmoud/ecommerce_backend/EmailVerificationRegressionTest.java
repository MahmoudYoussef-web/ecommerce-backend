package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.auth.RegisterRequest;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.UserStatus;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 close-out regression test for email verification.
 *
 * Runs with the dev auto-verify flag OFF so the real flow is exercised:
 * register -> PENDING_VERIFICATION (login blocked) -> verify-email -> ACTIVE
 * (login allowed). Verifying twice is a no-op (idempotent).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.dev.auto-verify-email=false"
)
class EmailVerificationRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void unverifiedUserCannotLoginUntilEmailVerified() {
        String email = "verify-" + UUID.randomUUID() + "@test.com";
        register(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationToken()).isNotBlank();

        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> verify = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify-email?token=" + user.getVerificationToken(),
                Map.class
        );
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);

        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(verified.getVerificationToken())
                .as("token is retained so re-verification stays idempotent")
                .isNotBlank();

        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyingAnAlreadyVerifiedAccountIsAnIdempotentNoOp() {
        String email = "verify-again-" + UUID.randomUUID() + "@test.com";
        register(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        String token = user.getVerificationToken();

        String url = baseUrl() + "/api/auth/verify-email?token=" + token;

        assertThat(restTemplate.getForEntity(url, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity(url, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private void register(String email) {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Verify")
                .lastName("Test")
                .email(email)
                .password("Password123!")
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                request,
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("Password123!");

        return restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                request,
                Map.class
        );
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
