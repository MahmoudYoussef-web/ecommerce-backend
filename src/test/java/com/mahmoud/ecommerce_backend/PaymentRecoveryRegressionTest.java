package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.payment.CreatePaymentRequest;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.OrderItem;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.service.payment.PaymentService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 regression: a FAILED or CANCELLED payment must not permanently
 * brick its order. Retrying createPayment rebases the single payment row to
 * PENDING (audit preserved on the same row) so checkout can start a new
 * session. Webhook processing stays replay-safe and amount-verified, and a
 * completed payment still blocks any further payment creation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentRecoveryRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    private Long productId;

    private Long orderId;

    @BeforeEach
    void seedOrder() {

TestBuckets.reset(rateLimitFilter);
        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Recovery Category")
                        .slug("rec-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Recovery Product")
                        .slug("rec-prod-" + UUID.randomUUID())
                        .sku("REC-" + UUID.randomUUID())
                        .price(new BigDecimal("50.00"))
                        .stockQuantity(10)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .reviewCount(0)
                        .category(category)
                        .build()
        );
        productId = product.getId();

        Order order = Order.builder()
                .orderNumber("REC-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("50.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Recovery Test")
                        .addressLine1("1 Recovery St")
                        .city("Cairo")
                        .postalCode("11511")
                        .country("Egypt")
                        .build())
                .build();
        OrderItem item = OrderItem.builder()
                .productId(productId)
                .productName(product.getName())
                .productSku(product.getSku())
                .priceAtPurchase(new BigDecimal("50.00"))
                .quantity(1)
                .build();
        order.addItem(item);
        orderId = orderRepository.save(order).getId();

        // The order flow reserves stock at creation time.
        reservationRepository.save(
                StockReservation.builder()
                        .productId(productId)
                        .quantity(1)
                        .status(StockReservationStatus.RESERVED)
                        .orderId(orderId)
                        .expiresAt(java.time.Instant.now().plusSeconds(900))
                        .build()
        );
    }

    @AfterEach
    void cleanup() {
        if (orderId != null) {
            jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stock_reservations WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        if (productId != null) {
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        }
        jdbcTemplate.update("DELETE FROM categories WHERE slug LIKE 'rec-cat-%'");
        orderId = null;
        productId = null;
    }

    private String customerToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@gmail.com");
        request.setPassword("123456");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", request, Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    private ResponseEntity<Map> createPaymentHttp(String token) {
        CreatePaymentRequest body = new CreatePaymentRequest();
        body.setOrderId(orderId);
        body.setMethod(PaymentMethod.STRIPE);
        return restTemplate.exchange(
                baseUrl() + "/api/payments",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                Map.class
        );
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void failedPaymentCanBeRetried() {
        String token = customerToken();

        ResponseEntity<Map> first = createPaymentHttp(token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long paymentId = ((Number) ((Map<?, ?>) first.getBody().get("data")).get("id")).longValue();

        // Gateway reports failure.
        paymentService.processWebhook("evt-recover-fail-" + UUID.randomUUID(), paymentId,
                PaymentStatus.FAILED, "declined");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        // Old behaviour: "Payment already exists" — order bricked forever.
        // New behaviour: rebase the single row to PENDING so retry works,
        // and RE-RESERVE the inventory that the failure released.
        ResponseEntity<Map> retry = createPaymentHttp(token);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        // Exactly one payment row still exists for the order.
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getId())
                .isEqualTo(paymentId);

        // The audit trail of the failed attempt survives on the same row.
        Payment rebased = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(rebased.getGatewayResponse()).contains("rebase");

        // A fresh RESERVED reservation exists for the retry (the original was
        // released by handleFailure), and completing the retried payment now
        // confirms it: stock decremented once, order PAID.
        var reservations = reservationRepository.findAllByOrderId(orderId);
        assertThat(reservations).anySatisfy(r ->
                assertThat(r.getStatus()).isEqualTo(StockReservationStatus.RESERVED));

        paymentService.processWebhook("evt-recover-complete-" + UUID.randomUUID(), paymentId,
                PaymentStatus.COMPLETED, "pi_retry_success");

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(9);
    }

    @Test
    void cancelledPaymentCanBeRetried() {
        String token = customerToken();

        createPaymentHttp(token);
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        // Stripe session expires -> CANCELLED.
        paymentService.processWebhook("evt-recover-cancel-" + UUID.randomUUID(), paymentId,
                PaymentStatus.CANCELLED, "session expired");

        ResponseEntity<Map> retry = createPaymentHttp(token);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void completedPaymentStillBlocksNewPayment() {
        String token = customerToken();

        createPaymentHttp(token);
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        paymentService.processWebhook("evt-recover-paid-" + UUID.randomUUID(), paymentId,
                PaymentStatus.COMPLETED, "pi_test");

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);

        // Paid order: no further payment creation.
        ResponseEntity<Map> again = createPaymentHttp(token);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateWebhookEventIsIdempotent() {
        String token = customerToken();
        createPaymentHttp(token);
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        String eventId = "evt-dup-" + UUID.randomUUID();
        paymentService.processWebhook(eventId, paymentId, PaymentStatus.COMPLETED, "pi_dup");
        paymentService.processWebhook(eventId, paymentId, PaymentStatus.COMPLETED, "pi_dup");

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void webhookWithWrongAmountIsRejected() {
        String token = customerToken();
        createPaymentHttp(token);
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        String eventId = "evt-amount-" + UUID.randomUUID();
        assertThatThrownBy(() -> paymentService.processStripeWebhook(
                eventId, paymentId, PaymentStatus.COMPLETED, "pi_x",
                new BigDecimal("999.99"), "USD"))
                .isInstanceOf(ForbiddenException.class);

        // Payment untouched by the rejected event.
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }
}
