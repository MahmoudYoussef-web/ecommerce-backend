package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
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
 * Phase 1 close-out regression test for the COD "mark paid" admin endpoint.
 *
 * Verifies:
 * - only ADMIN may mark a COD order as paid (customer 403, anonymous 401)
 * - the first call creates exactly one COD payment, completes it and the order,
 *   and confirms stock reservations (a single fulfillment)
 * - a second call is an idempotent no-op: no duplicate payment, no second
 *   stock decrement, no double-fulfillment
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodMarkPaidRegressionTest {

    private static final int INITIAL_STOCK = 10;
    private static final int RESERVED_QTY = 3;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long orderId;
    private Long productId;
    private Long reservationId;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        cleanup();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("COD Category")
                        .slug("cod-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("COD Product")
                        .slug("cod-prod-" + UUID.randomUUID())
                        .sku("COD-" + UUID.randomUUID())
                        .price(new BigDecimal("120.00"))
                        .stockQuantity(INITIAL_STOCK)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .reviewCount(0)
                        .category(category)
                        .build()
        );
        productId = product.getId();

        User user = userRepository.findByEmail("user@gmail.com")
                .orElseThrow(() -> new IllegalStateException("Seeded user missing"));

        Order order = Order.builder()
                .orderNumber("COD-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("120.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("120.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Test User")
                        .addressLine1("1 Test Street")
                        .city("Cairo")
                        .postalCode("11511")
                        .country("Egypt")
                        .build())
                .build();
        orderId = orderRepository.save(order).getId();

        StockReservation reservation = reservationRepository.save(
                StockReservation.builder()
                        .productId(productId)
                        .quantity(RESERVED_QTY)
                        .status(StockReservationStatus.RESERVED)
                        .expiresAt(Instant.now().plusSeconds(900))
                        .orderId(orderId)
                        .build()
        );
        reservationId = reservation.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void adminMarkPaidCompletesCodPaymentAndFulfillsOnce() {
        ResponseEntity<String> resp = markPaid(adminToken());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getGatewayReference()).startsWith("COD_ADMIN:");

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - RESERVED_QTY);

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(StockReservationStatus.CONFIRMED);
    }

    @Test
    void secondMarkPaidCallIsAnIdempotentNoOp() {
        assertThat(markPaid(adminToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        Instant paidAtAfterFirstCall = payment.getPaidAt();

        assertThat(markPaid(adminToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        Payment reloaded = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloaded.getPaidAt()).isEqualTo(paidAtAfterFirstCall);
        assertThat(reloaded.getGatewayReference()).startsWith("COD_ADMIN:");

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .as("stock must be decremented exactly once (no double fulfillment)")
                .isEqualTo(INITIAL_STOCK - RESERVED_QTY);

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(StockReservationStatus.CONFIRMED);
    }

    @Test
    void customerCannotMarkCodOrderAsPaid() {
        ResponseEntity<String> resp = markPaid(customerToken());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void anonymousCannotMarkCodOrderAsPaid() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/payments/" + orderId + "/mark-paid",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    private ResponseEntity<String> markPaid(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        return restTemplate.exchange(
                baseUrl() + "/api/payments/" + orderId + "/mark-paid",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );
    }

    private String adminToken() {
        if (adminToken == null) {
            adminToken = loginAndGetToken("admin@gmail.com", "123456");
        }
        return adminToken;
    }

    private String customerToken() {
        if (customerToken == null) {
            customerToken = loginAndGetToken("user@gmail.com", "123456");
        }
        return customerToken;
    }

    private String loginAndGetToken(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                request,
                Map.class
        );

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
        jdbcTemplate.update("DELETE FROM product_variant_attributes");
        jdbcTemplate.update("DELETE FROM product_variants WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM product_images WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM products WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM categories WHERE tenant_id = 1");
    }
}
