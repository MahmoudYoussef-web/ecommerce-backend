package com.mahmoud.ecommerce_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: production-profile exposure rules.
 *
 * Boots the REAL prod profile against a dedicated throwaway database:
 *  - Swagger / OpenAPI are disabled -> anonymous requests get clean 404s
 *  - /actuator/health stays reachable without credentials (mail health is DOWN
 *    in this environment, so 503 with a status body also proves reachability;
 *    what must never happen is 401/403/404)
 *  - any other actuator endpoint is walled off behind ADMIN for anonymous users
 *
 * Also exercises V1..V6 Flyway migrations on a fresh database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=prod",
        "DB_URL=jdbc:mysql://localhost:3306/ecommerce_db_prod_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        "AUTH_JWT_SECRET=uuc4x1fAzT9aaaQuFBpRfmqrDXhH0TeloMr6IweyGEvPJrxjffdTvdsPHswrZcU+hwvUCpi5BXL4N40bX+xLaA==",
        "STRIPE_SECRET_KEY=sk_test_placeholder",
        "STRIPE_WEBHOOK_SECRET=whsec_placeholder",
        "PAYMENT_WEBHOOK_SECRET=test_webhook_secret",
        "APP_BASE_URL=http://localhost:8080",
        "APP_FRONTEND_URL=http://localhost:5173",
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173"
})
class SwaggerAndActuatorProdSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void swaggerUiIsNotServedInProd() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/swagger-ui/index.html", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void openApiDocsAreNotServedInProd() {
        ResponseEntity<String> docs = restTemplate.getForEntity(
                baseUrl() + "/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> yaml = restTemplate.getForEntity(
                baseUrl() + "/v3/api-docs.yaml", String.class);
        assertThat(yaml.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthEndpointRemainsReachableWithoutCredentials() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);

        // Reachable anonymously. In this environment the mail health indicator
        // reports DOWN (placeholder SMTP host), which yields 503 + status body.
        // A wall (401/403/404) would mean the endpoint is no longer public.
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).contains("status");
    }

    @Test
    void otherActuatorEndpointsStayWalledOffFromAnonymousUsers() {
        ResponseEntity<String> metrics = restTemplate.exchange(
                baseUrl() + "/actuator/metrics", HttpMethod.GET, null, String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
