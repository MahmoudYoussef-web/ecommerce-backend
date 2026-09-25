package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: actuator exposure policy and error-response hygiene.
 *
 * Actuator:
 *  - anonymous /actuator/health stays public
 *  - CUSTOMER is walled off from everything else under /actuator/**
 *  - ADMIN gets past the security wall; unexposed endpoints then 404
 *
 * Error disclosure:
 *  - DB constraint violations return a generic message (no SQL fragments,
 *    constraint names, table names)
 *  - unknown paths return a clean 404 envelope
 *  - every response carries X-Trace-Id for log correlation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorAndErrorDisclosureRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.mahmoud.ecommerce_backend.repository.CategoryRepository categoryRepository;

    private Long categoryId;

    @BeforeEach
    void ensureSeededUserActive() {

TestBuckets.reset(rateLimitFilter);
        jdbcTemplate.update(
                "UPDATE users SET enabled = 1, account_non_locked = 1, email_verified = 1, status = 'ACTIVE' " +
                        "WHERE email = 'user@gmail.com'");
        if (categoryId == null) {
            categoryId = categoryRepository.save(
                    com.mahmoud.ecommerce_backend.entity.Category.builder()
                            .name("DisclosureProbe-" + java.util.UUID.randomUUID())
                            .slug("disclosure-probe-" + java.util.UUID.randomUUID())
                            .displayOrder(0)
                            .active(true)
                            .build()).getId();
        }
    }

    // ---------- actuator ----------

    @Test
    void healthRemainsPublic() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void customerIsWalledOffFromActuatorInternals() {
        String token = customerToken();

        ResponseEntity<String> metrics = restTemplate.exchange(
                baseUrl() + "/actuator/metrics",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminPassesSecurityWall_unexposedEndpointsThenReturn404() {
        String token = adminToken();

        // Security gate passes for ADMIN; the endpoint itself is not exposed.
        ResponseEntity<String> metrics = restTemplate.exchange(
                baseUrl() + "/actuator/metrics",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- error disclosure ----------

    @Test
    void duplicateSkuViolationReturnsGenericMessageWithoutDbDetails() {
        String token = adminToken();
        Long categoryId = existingCategoryId();

        Map<String, Object> payload = Map.of(
                "name", "Disclosure Probe " + System.nanoTime(),
                "price", "5.00",
                "stockQuantity", 1,
                "categoryId", categoryId,
                "sku", "DISCL-" + UUID.randomUUID().toString().substring(0, 8),
                "slug", "discl-" + UUID.randomUUID().toString().substring(0, 8)
        );

        ResponseEntity<Map> first = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(payload, jsonBearer(token)),
                Map.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();

        // Same SKU again -> unique constraint fires at the database level.
        ResponseEntity<Map> second = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(payload, jsonBearer(token)),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String body = String.valueOf(second.getBody());

        assertThat(body).doesNotContain("Duplicate entry");
        assertThat(body).doesNotContain("uk_");
        assertThat(body).doesNotContain("for key");
        assertThat(body).doesNotContain("CONSTRAINT");
        assertThat(body.toLowerCase()).doesNotContain("insert into");
    }

    @Test
    void unknownPathReturnsClean404EnvelopeWithTraceId() {
        // Authenticated so the request passes the security chain and reaches
        // MVC dispatch, proving the no-handler mapping itself (not the auth
        // wall) produces the 404 envelope.
        String token = customerToken();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/definitely-not-a-real-path",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(resp.getBody())).contains("NOT_FOUND");

        String traceId = resp.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).as("X-Trace-Id must be echoed to clients").isNotBlank();
        assertThat(traceId).matches("[0-9a-f-]{36}");
    }

    @Test
    void traceIdHeaderIsPresentOnRegularResponsesToo() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/api/products?page=0&size=1", Map.class);
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }

    // ---------- helpers ----------

    private String customerToken() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@gmail.com");
        req.setPassword("123456");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String adminToken() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@gmail.com");
        req.setPassword("123456");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private Long existingCategoryId() {
        return categoryId;
    }

    private HttpHeaders jsonBearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
