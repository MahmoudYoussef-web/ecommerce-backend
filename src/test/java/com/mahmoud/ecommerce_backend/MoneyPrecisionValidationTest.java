package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 regression: monetary values that violate the entity precision
 * (@Digits(10,2)) must surface as a 400 VALIDATION_ERROR with a field map,
 * never as an unhandled 500 from a flush-time constraint violation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MoneyPrecisionValidationTest {

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

    private Long createdProductId;

    @org.junit.jupiter.api.BeforeEach
    void seedCategory() {
        categoryId = categoryRepository.save(
                com.mahmoud.ecommerce_backend.entity.Category.builder()
                        .name("Money Category " + UUID.randomUUID())
                        .slug("money-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        ).getId();
    }

    private String adminToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@gmail.com");
        request.setPassword("123456");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", request, Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> productBody(BigDecimal price, BigDecimal discountedPrice) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Money Precision " + UUID.randomUUID());
        body.put("slug", "money-" + UUID.randomUUID());
        body.put("sku", "MNY-" + UUID.randomUUID());
        body.put("description", "precision test");
        body.put("price", price);
        if (discountedPrice != null) {
            body.put("discountedPrice", discountedPrice);
        }
        body.put("stockQuantity", 5);
        body.put("categoryId", categoryId);
        body.put("imageUrls", java.util.List.of());
        return body;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @AfterEach
    void cleanup() {
        if (createdProductId != null) {
            jdbcTemplate.update("DELETE FROM product_images WHERE product_id = ?", createdProductId);
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", createdProductId);
            createdProductId = null;
        }
        if (categoryId != null) {
            jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
            categoryId = null;
        }
    }

    @BeforeEach
    void resetRateLimitBuckets() {
        TestBuckets.reset(rateLimitFilter);
    }

    @Test
    void createWithExcessiveDecimalScaleReturns400Not500() {
        String token = adminToken();

        // 10.999 has 3 decimal places — violates @Digits(fraction=2).
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productBody(new BigDecimal("10.999"), null), bearer(token)),
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(resp.getBody())).contains("VALIDATION_ERROR");
        assertThat(String.valueOf(resp.getBody())).contains("price");

        createdProductId = null;
    }

    @Test
    void updateWithExcessiveDecimalScaleReturns400Not500() {
        String token = adminToken();

        // Seed a valid product first.
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productBody(new BigDecimal("10.00"), null), bearer(token)),
                Map.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        createdProductId = ((Number) ((Map<?, ?>) created.getBody().get("data")).get("id")).longValue();

        Map<String, Object> update = Map.of(
                "name", "Money Precision Updated",
                "price", new BigDecimal("12.3456")
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/products/" + createdProductId,
                HttpMethod.PUT,
                new HttpEntity<>(update, bearer(token)),
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(resp.getBody())).contains("VALIDATION_ERROR");

        // The stored price must be untouched by the rejected update
        // (compare numerically — JSON renders 10.00 as 10.0).
        ResponseEntity<Map> reloaded = restTemplate.exchange(
                baseUrl() + "/api/products/" + createdProductId,
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                Map.class
        );
        Map<?, ?> data = (Map<?, ?>) reloaded.getBody().get("data");
        assertThat(new BigDecimal(String.valueOf(data.get("price"))))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void validTwoDecimalPriceIsAccepted() {
        String token = adminToken();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productBody(new BigDecimal("10.99"), new BigDecimal("8.50")), bearer(token)),
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        createdProductId = ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }

    @Test
    void negativePriceIsRejected() {
        String token = adminToken();

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productBody(new BigDecimal("-1.00"), null), bearer(token)),
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
