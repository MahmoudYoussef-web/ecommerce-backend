package com.mahmoud.ecommerce_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.*;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 regression coverage:
 * - Address book API (create / list / delete / ownership)
 * - Order requires addressId (400 on null)
 * - Review submission (comment persisted, duplicate rule, validation, auto-approval, aggregates)
 * - Product listing sort whitelist + ordering
 * - Admin orders server-side pagination, status filter, search, authorization
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Phase3IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long productId;
    private Long categoryId;

    // One token pair per class instance — the per-IP login rate limiter
    // (capacity 10) is shared by every test class hitting the cached context,
    // so each class must keep its auth traffic to an absolute minimum.
    private String customerToken;
    private String adminToken;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        TestBuckets.reset(rateLimitFilter);
        cleanup();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Phase3 Test Category")
                        .slug("p3-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );
        categoryId = category.getId();

        Product product = productRepository.save(
                Product.builder()
                        .name("Phase3 Test Product")
                        .slug("p3-prod-" + UUID.randomUUID())
                        .sku("P3-" + UUID.randomUUID())
                        .price(new BigDecimal("100.00"))
                        .stockQuantity(50)
                        .reviewCount(0)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .category(category)
                        .build()
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        TenantContext.clear();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stock_reservations WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM payments WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM order_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM carts WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM cart_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM addresses WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM reviews WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM wishlist_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM wishlists WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM products WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM categories WHERE tenant_id = 1");
    }

    private String login(String email) throws Exception {
        if ("user@gmail.com".equals(email) && customerToken != null) return customerToken;
        if ("admin@gmail.com".equals(email) && adminToken != null) return adminToken;

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("123456");

        // The per-IP login bucket (capacity 10, ~1 token / 3s refill) is shared
        // by every test class against the cached context. Wait out occasional
        // 429s instead of failing on suite-ordering luck.
        ResponseEntity<String> response = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            response = restTemplate.postForEntity(baseUrl() + "/api/auth/login", request, String.class);
            if (response.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) break;
            Thread.sleep(3500L * (attempt + 1));
        }

        assertThat(response.getStatusCode())
                .as("login response body: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
        String token = (String) body.get("accessToken");

        if ("user@gmail.com".equals(email)) customerToken = token;
        else adminToken = token;
        return token;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(ResponseEntity<String> response) throws Exception {
        return objectMapper.readValue(response.getBody(), Map.class);
    }

    /* ─────────────── 3.1 Address book ─────────────── */

    @Test
    void addressBook_create_list_delete_and_ownership() throws Exception {
        String customerToken = login("user@gmail.com");
        String adminToken = login("admin@gmail.com");

        // Create
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Phase3 Tester");
        payload.put("phone", "0123456789");
        payload.put("country", "Egypt");
        payload.put("city", "Cairo");
        payload.put("state", "Cairo");
        payload.put("street", "12 Tahrir Square");
        payload.put("zipCode", "12345");
        payload.put("label", "work");
        payload.put("isDefault", false);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                baseUrl() + "/api/addresses", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(customerToken)), String.class);

        assertThat(createResponse.getStatusCode())
                .as("create address response body: %s", createResponse.getBody())
                .isEqualTo(HttpStatus.OK);
        // AddressController returns the DTO directly (no ApiResponse wrapper)
        Map<String, Object> created = objectMapper.readValue(createResponse.getBody(), Map.class);
        Long addressId = ((Number) created.get("id")).longValue();
        assertThat(created.get("fullName")).isEqualTo("Phase3 Tester");
        assertThat(created.get("street")).isEqualTo("12 Tahrir Square");
        assertThat(created.get("label")).isEqualTo("work");

        // List contains it with the fields needed by the address book UI
        ResponseEntity<String> listResponse = restTemplate.exchange(
                baseUrl() + "/api/addresses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)), String.class);
        List<Map<String, Object>> items =
                objectMapper.readValue(listResponse.getBody(), List.class);
        assertThat(items).anyMatch(a ->
                ((Number) a.get("id")).longValue() == addressId
                        && "Phase3 Tester".equals(a.get("fullName"))
                        && a.containsKey("isDefault"));

        // Ownership: another user cannot delete it → 403
        ResponseEntity<String> forbiddenDelete = restTemplate.exchange(
                baseUrl() + "/api/addresses/" + addressId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)), String.class);
        assertThat(forbiddenDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Owner can delete
        ResponseEntity<String> ownerDelete = restTemplate.exchange(
                baseUrl() + "/api/addresses/" + addressId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(customerToken)), String.class);
        assertThat(ownerDelete.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> listAfterDelete = restTemplate.exchange(
                baseUrl() + "/api/addresses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerToken)), String.class);
        List<Map<String, Object>> remaining =
                objectMapper.readValue(listAfterDelete.getBody(), List.class);
        assertThat(remaining).noneMatch(a ->
                ((Number) a.get("id")).longValue() == addressId);
    }

    @Test
    void order_without_addressId_is_rejected_with_400() throws Exception {
        String token = login("user@gmail.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("customerNotes", "no address");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /* ─────────────── 3.2 Reviews ─────────────── */

    @Test
    void review_create_persists_comment_and_autoApproves() throws Exception {
        String token = login("user@gmail.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("rating", 5);
        payload.put("comment", "Excellent build quality");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/reviews", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) readBody(response).get("data");
        assertThat(data.get("comment")).isEqualTo("Excellent build quality");

        // Public list shows the approved review immediately
        ResponseEntity<String> listResponse = restTemplate.getForEntity(
                baseUrl() + "/api/reviews/product/" + productId, String.class);
        List<Map<String, Object>> reviews =
                (List<Map<String, Object>>) readBody(listResponse).get("data");
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).get("comment")).isEqualTo("Excellent build quality");

        // Aggregates recalculated for the product card stars
        ResponseEntity<String> productResponse = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + productId, String.class);
        Map<String, Object> product = (Map<String, Object>) readBody(productResponse).get("data");
        assertThat(((Number) product.get("reviewCount")).intValue()).isEqualTo(1);
        assertThat(new BigDecimal(String.valueOf(product.get("averageRating"))))
                .isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void review_duplicate_is_rejected_with_400() throws Exception {
        String token = login("user@gmail.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("rating", 4);
        payload.put("comment", "first");

        ResponseEntity<String> first = restTemplate.exchange(
                baseUrl() + "/api/reviews", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        payload.put("rating", 2);
        payload.put("comment", "second");
        ResponseEntity<String> second = restTemplate.exchange(
                baseUrl() + "/api/reviews", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void review_validation_rejects_outOfRange_rating() throws Exception {
        String token = login("user@gmail.com");

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("rating", 6);
        payload.put("comment", "too good");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/reviews", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void review_requires_authentication() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("rating", 5);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/reviews", HttpMethod.POST,
                new HttpEntity<>(payload), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /* ─────────────── 3.3 Product sort ─────────────── */

    @Test
    void product_sort_whitelist_and_ordering() throws Exception {
        productRepository.save(Product.builder()
                .name("Cheap").slug("p3-cheap-" + UUID.randomUUID()).sku("P3C-" + UUID.randomUUID())
                .price(new BigDecimal("10.00")).stockQuantity(5).lowStockThreshold(0)
                .status(ProductStatus.ACTIVE).category(categoryRepository.findById(categoryId).orElseThrow())
                .build());
        productRepository.save(Product.builder()
                .name("Mid").slug("p3-mid-" + UUID.randomUUID()).sku("P3M-" + UUID.randomUUID())
                .price(new BigDecimal("50.00")).stockQuantity(5).lowStockThreshold(0)
                .status(ProductStatus.ACTIVE).category(categoryRepository.findById(categoryId).orElseThrow())
                .build());

        ResponseEntity<String> sorted = restTemplate.getForEntity(
                baseUrl() + "/api/products/search?categoryId=" + categoryId
                        + "&sort=price_asc&page=0&size=20",
                String.class);
        assertThat(sorted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> pageData = (Map<String, Object>) readBody(sorted).get("data");
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) pageData.get("content");
        List<BigDecimal> prices = content.stream()
                .map(p -> new BigDecimal(String.valueOf(p.get("price"))))
                .toList();
        // Scale-insensitive ascending check: sorted and cheapest-first.
        assertThat(prices).isSortedAccordingTo(BigDecimal::compareTo);
        assertThat(prices.get(0)).isEqualByComparingTo(new BigDecimal("10.00"));

        ResponseEntity<String> invalid = restTemplate.getForEntity(
                baseUrl() + "/api/products/search?sort=cost_asc", String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /* ─────────────── 3.4 Admin orders paging ─────────────── */

    private void seedOrder(User user, OrderStatus status, String number) {
        orderRepository.save(Order.builder()
                .orderNumber(number)
                .user(user)
                .status(status)
                .totalAmount(new BigDecimal("250.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Phase3 Tester")
                        .addressLine1("1 Test Street")
                        .city("Cairo")
                        .postalCode("12345")
                        .country("Egypt")
                        .build())
                .build());
    }

    @Test
    void adminOrders_paginated_filtered_and_roleGuarded() throws Exception {
        User customer = userRepository.findByEmail("user@gmail.com").orElseThrow();
        seedOrder(customer, OrderStatus.PENDING, "P3-ORD-" + UUID.randomUUID());
        seedOrder(customer, OrderStatus.PENDING, "P3-ORD-" + UUID.randomUUID());
        seedOrder(customer, OrderStatus.CANCELLED, "P3-ORD-" + UUID.randomUUID());

        String adminToken = login("admin@gmail.com");

        // Pagination math
        ResponseEntity<String> page = restTemplate.exchange(
                baseUrl() + "/api/admin/orders?page=0&size=2",
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) readBody(page).get("data");
        assertThat(((Number) data.get("totalElements")).longValue()).isEqualTo(3L);
        assertThat(((Number) data.get("totalPages")).intValue()).isEqualTo(2);
        assertThat((List<?>) data.get("content")).hasSize(2);

        // Second page returns the remaining row
        ResponseEntity<String> page2 = restTemplate.exchange(
                baseUrl() + "/api/admin/orders?page=1&size=2",
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        Map<String, Object> data2 = (Map<String, Object>) readBody(page2).get("data");
        assertThat((List<?>) data2.get("content")).hasSize(1);

        // Status filter keeps pagination correct
        ResponseEntity<String> pendingOnly = restTemplate.exchange(
                baseUrl() + "/api/admin/orders?page=0&size=10&status=PENDING",
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        Map<String, Object> pendingData = (Map<String, Object>) readBody(pendingOnly).get("data");
        assertThat(((Number) pendingData.get("totalElements")).longValue()).isEqualTo(2L);
        List<Map<String, Object>> pendingContent =
                (List<Map<String, Object>>) pendingData.get("content");
        assertThat(pendingContent).allMatch(o -> "PENDING".equals(o.get("status")));

        // Search by order number
        String targetNumber = ((List<Map<String, Object>>) data.get("content"))
                .get(0).get("orderNumber").toString();
        ResponseEntity<String> search = restTemplate.exchange(
                baseUrl() + "/api/admin/orders?q=" + targetNumber,
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> searchData = (Map<String, Object>) readBody(search).get("data");
        assertThat(((Number) searchData.get("totalElements")).longValue()).isEqualTo(1L);

        // Invalid status filter → 400
        ResponseEntity<String> badStatus = restTemplate.exchange(
                baseUrl() + "/api/admin/orders?status=NOT_A_STATUS",
                HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
        assertThat(badStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Customer token must not access admin orders
        String customerToken = login("user@gmail.com");
        ResponseEntity<String> forbidden = restTemplate.exchange(
                baseUrl() + "/api/admin/orders",
                HttpMethod.GET, new HttpEntity<>(authHeaders(customerToken)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
