package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.auth.RegisterRequest;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: IDOR on order and payment resources.
 *
 * User A must never read user B's order nor bootstrap a checkout session for
 * B's payment. (Cart and wishlist expose no resource-id routes at all — they
 * are resolved strictly from the authenticated principal server-side.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdorRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.mahmoud.ecommerce_backend.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.mahmoud.ecommerce_backend.repository.UserRepository userRepository;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private String attackerToken;

    private Long victimOrderId;

    private Long victimPaymentId;

    @BeforeEach
    void seed() {
        Object buckets = org.springframework.test.util.ReflectionTestUtils.getField(rateLimitFilter, "buckets");
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) buckets).invalidateAll();

        String victimEmail = registerUser("victim");
        attackerToken = login(registerUser("attacker"));

        var victim = userRepository.findByEmail(victimEmail).orElseThrow();

        Order order = Order.builder()
                .orderNumber("IDOR-" + UUID.randomUUID())
                .user(victim)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("50.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Victim").addressLine1("1 Secret Rd")
                        .city("Cairo").postalCode("11511").country("Egypt")
                        .build())
                .build();
        victimOrderId = orderRepository.save(order).getId();

        victimPaymentId = paymentRepository.save(
                Payment.create(order, PaymentMethod.STRIPE,
                        new BigDecimal("50.00"), "USD")).getId();
    }

    @Test
    void userCannotReadAnotherUsersOrder() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/orders/" + victimOrderId,
                HttpMethod.GET,
                new HttpEntity<>(bearer(attackerToken)),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userCannotBootstrapCheckoutForAnotherUsersPayment() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/payments/checkout/" + victimPaymentId,
                HttpMethod.POST,
                new HttpEntity<>(bearer(attackerToken)),
                Map.class);

        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
        assertThat(String.valueOf(resp.getBody()))
                .doesNotContain("checkout.stripe.com")
                .doesNotContain("url");
    }

    // ---------- helpers ----------

    private String registerUser(String prefix) {
        String email = prefix + "-idor-" + UUID.randomUUID() + "@test.com";
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                RegisterRequest.builder()
                        .firstName(prefix).lastName("Probe")
                        .email(email).password("Password123!")
                        .build(),
                Map.class);
        return email;
    }

    private String login(String email) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword("Password123!");
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
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
