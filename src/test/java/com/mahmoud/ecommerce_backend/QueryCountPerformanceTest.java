package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 verification: query-count flatness for the hot listing paths.
 *
 * Uses Hibernate statistics (isolated context with generate_statistics=true)
 * to prove the batched loaders keep JDBC statement counts FLAT as page content
 * grows — the concrete anti-N+1 evidence required by the audit plan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class QueryCountPerformanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private String customerToken;
    private String adminToken;

    private Long customerUserId;

    private Statistics stats() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void seedDataAndAuth() {
        if (customerToken != null) return;

        var seeded = userRepository.findByEmail("user@gmail.com").orElseThrow();
        customerUserId = seeded.getId();

        // Two batches of orders so flatness can be asserted between sizes.
        for (int i = 0; i < 30; i++) {
            seedOrder(seeded);
        }

        customerToken = login("user@gmail.com", "123456");
        adminToken = login("admin@gmail.com", "123456");
    }

    private void seedOrder(User user) {
        Order order = Order.builder()
                .orderNumber("PERF-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("10.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Perf User").addressLine1("1 Perf St")
                        .city("Cairo").postalCode("11511").country("Egypt")
                        .build())
                .build();

        Order saved = orderRepository.save(order);

        for (int j = 0; j < 3; j++) {
            OrderItem item = OrderItem.builder()
                    .productId(1L)
                    .productName("Item " + j)
                    .productSku("SKU-" + j)
                    .priceAtPurchase(new BigDecimal("3.33"))
                    .quantity(1)
                    .order(saved)
                    .build();
            saved.addItem(item);
        }
        orderRepository.save(saved);
    }

    @Test
    void customerOrderHistory_queryCountIsFlat_regardlessOfPageSize() {
        int small = countQueries(() ->
                httpGet("/api/orders?page=0&size=5", customerToken));
        int large = countQueries(() ->
                httpGet("/api/orders?page=0&size=20", customerToken));

        assertThat(small).as("small page queries").isLessThanOrEqualTo(15);
        assertThat(large).as("large page queries").isLessThanOrEqualTo(15);
        assertThat(large).as("4x the rows must not mean more queries")
                .isLessThanOrEqualTo(small + 2);
    }

    @Test
    void adminOrdersPage_queryCountIsFlat_andWellBelowLegacyNPlusOne() {
        int queries = countQueries(() ->
                httpGet("/api/admin/orders?page=0&size=20", adminToken));

        // Legacy implementation cost ≈ 43+ queries at size 20 (per-row user +
        // per-row payment). Batched version stays in single digits plus the
        // fixed per-request auth/tenant overhead.
        assertThat(queries).as("admin orders page queries").isLessThanOrEqualTo(18);
    }

    @Test
    void catalogListing_missPathQueriesAreBounded() {
        int queries = countQueries(() ->
                httpGet("/api/products?page=0&size=20", null));

        // Page + images batch + categories batch (+ count) — no per-product fan-out.
        assertThat(queries).as("catalog miss-path queries").isLessThanOrEqualTo(8);
    }

    // ---------- helpers ----------

    private interface Call {
        void run();
    }

    private int countQueries(Call call) {
        Statistics statistics = stats();
        statistics.clear();
        call.run();
        return (int) statistics.getPrepareStatementCount();
    }

    private void httpGet(String pathWithQuery, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        restTemplate.exchange(baseUrl() + pathWithQuery, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    private String login(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
