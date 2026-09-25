package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.CouponType;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.repository.CouponRepository;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.service.coupon.CouponService;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 regression tests for the coupon engine and return requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAndReturnRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        TestBuckets.reset(rateLimitFilter);
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void adminCreatesCouponAndCustomerIsForbidden() {
        Map<String, Object> body = Map.of(
                "code", "TEST10",
                "type", "PERCENT",
                "value", new BigDecimal("10.00"));

        ResponseEntity<Map> created = post("/api/coupons", adminToken(), body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> forbidden = postRaw("/api/coupons", customerToken(), body);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validateEndpointComputesPercentDiscount() {
        couponService.create(request("EVAL20", CouponType.PERCENT, "20.00", null, null, null));

        ResponseEntity<Map> resp = get(
                "/api/coupons/validate?code=EVAL20&subtotal=200.00", customerToken());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(new java.math.BigDecimal(String.valueOf(data.get("discount"))))
                .isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void invalidAndExpiredCouponsAreRejected() {
        ResponseEntity<String> invalid = getRaw(
                "/api/coupons/validate?code=NOPE&subtotal=100.00", customerToken());
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest expired =
                request("OLD", CouponType.FIXED, "5.00", null,
                        Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        couponService.create(expired);
        ResponseEntity<String> gone = getRaw(
                "/api/coupons/validate?code=OLD&subtotal=100.00", customerToken());
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void usageLimitIsEnforced() {
        com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest req =
                request("ONCE", CouponType.FIXED, "5.00", null, null, null);
        req.setUsageLimit(1);
        couponService.create(req);

        assertThat(couponService.applyCoupon("ONCE", new BigDecimal("50.00")))
                .isEqualByComparingTo(new BigDecimal("5.00"));

        try {
            couponService.applyCoupon("ONCE", new BigDecimal("50.00"));
            assertThat(false).as("second use must fail").isTrue();
        } catch (Exception ex) {
            assertThat(ex.getMessage()).contains("usage limit");
        }
    }

    @Test
    void customerCanRequestReturnAndAdminApproves() {
        Long orderId = deliveredOrder();

        ResponseEntity<String> req = postRaw(
                "/api/orders/" + orderId + "/return", customerToken(),
                Map.of("reason", "wrong size"));
        assertThat(req.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderRepository.findById(orderId).orElseThrow().isReturnRequested()).isTrue();

        ResponseEntity<String> approve = postRaw(
                "/api/orders/" + orderId + "/approve-return", adminToken(), Map.of());
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void returnOnNonDeliveredOrderIsRejected() {
        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();
        Order order = orderRepository.save(Order.builder()
                .orderNumber("RET-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("10.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("10.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("T").addressLine1("s").city("Cairo")
                        .postalCode("1").country("Egypt").build())
                .build());

        ResponseEntity<String> resp = postRaw(
                "/api/orders/" + order.getId() + "/return", customerToken(),
                Map.of("reason", "changed mind"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ================= helpers =================

    private com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest request(
            String code, CouponType type, String value,
            BigDecimal min, Instant from, Instant to) {
        return com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest.builder()
                .code(code).type(type).value(new BigDecimal(value))
                .minSubtotal(min).startsAt(from).endsAt(to).build();
    }

    private Long deliveredOrder() {
        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();
        Order order = Order.builder()
                .orderNumber("DLV-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("60.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("60.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("T").addressLine1("s").city("Cairo")
                        .postalCode("1").country("Egypt").build())
                .build();
        return orderRepository.save(order).getId();
    }

    private ResponseEntity<Map> post(String path, String token, Object body) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), Map.class);
    }

    private ResponseEntity<String> postRaw(String path, String token, Object body) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), String.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<String> getRaw(String path, String token) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), String.class);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String adminToken() {
        if (adminToken == null) adminToken = login("admin@gmail.com", "123456");
        return adminToken;
    }

    private String customerToken() {
        if (customerToken == null) customerToken = login("user@gmail.com", "123456");
        return customerToken;
    }

    private String login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", request, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stock_reservations WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM payments WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM order_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM cart_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM carts WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM addresses WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM stock_movements WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM journal_lines WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM journal_entries WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM reviews WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM wishlist_items WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM wishlists WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM coupons WHERE tenant_id = 1");
    }
}
