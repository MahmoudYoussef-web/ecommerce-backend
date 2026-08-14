package com.mahmoud.ecommerce_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.payment.CreatePaymentRequest;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.*;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EcommerceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.webhook.secret}")
    private String webhookSecret;

    private Long addressId;

    private String baseUrl() {
        return "http://localhost:" + port;
    }


    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        cleanup();

        User user = userRepository.findByEmail("user@gmail.com")
                .orElseThrow(() -> new IllegalStateException("Seeded user missing"));

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Integration Test Category")
                        .slug("it-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Integration Test Product")
                        .slug("it-prod-" + UUID.randomUUID())
                        .sku("IT-" + UUID.randomUUID())
                        .price(new BigDecimal("100.00"))
                        .stockQuantity(50)
                        .reviewCount(0)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .category(category)
                        .build()
        );

        Address address = addressRepository.save(
                Address.builder()
                        .user(user)
                        .fullName("Test User")
                        .phone("01000000000")
                        .addressLine1("1 Test Street")
                        .city("Cairo")
                        .state("Cairo")
                        .postalCode("12345")
                        .country("Egypt")
                        .addressType(AddressType.SHIPPING)
                        .isDefault(true)
                        .label("home")
                        .build()
        );
        addressId = address.getId();

        CartItem item = CartItem.builder()
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .totalPrice(new BigDecimal("200.00"))
                .build();

        Cart cart = Cart.builder()
                .user(user)
                .totalAmount(new BigDecimal("200.00"))
                .build();
        cart.addItem(item);

        cartRepository.save(cart);
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


    private String loginAndGetToken() throws Exception {

        LoginRequest request = new LoginRequest();
        request.setEmail("user@gmail.com");
        request.setPassword("123456");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body =
                objectMapper.readValue(response.getBody(), Map.class);

        return (String) body.get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(key);

        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }


    @Test
    void fullFlow_order_payment_inventory() throws Exception {

        String token = loginAndGetToken();


        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setAddressId(addressId);
        orderRequest.setCustomerNotes("test order");

        HttpEntity<CreateOrderRequest> orderEntity =
                new HttpEntity<>(orderRequest, authHeaders(token));

        ResponseEntity<String> orderResponse =
                restTemplate.postForEntity(
                        baseUrl() + "/api/orders",
                        orderEntity,
                        String.class
                );

        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> orderBody =
                objectMapper.readValue(orderResponse.getBody(), Map.class);

        Long orderId = ((Number) ((Map<?, ?>) orderBody.get("data")).get("id")).longValue();

        assertThat(orderId).isNotNull();


        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setMethod(PaymentMethod.CREDIT_CARD);

        HttpEntity<CreatePaymentRequest> paymentEntity =
                new HttpEntity<>(paymentRequest, authHeaders(token));

        ResponseEntity<String> paymentResponse =
                restTemplate.postForEntity(
                        baseUrl() + "/api/payments",
                        paymentEntity,
                        String.class
                );

        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> paymentBody =
                objectMapper.readValue(paymentResponse.getBody(), Map.class);

        Long paymentId = ((Number) ((Map<?, ?>) paymentBody.get("data")).get("id")).longValue();

        assertThat(paymentId).isNotNull();


        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("eventId", "evt_test_123");
        webhookPayload.put("paymentId", paymentId);
        webhookPayload.put("status", "COMPLETED");
        webhookPayload.put("reference", "stripe_test_ref");

        String rawBody = objectMapper.writeValueAsString(webhookPayload);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = hmacSha256Hex(webhookSecret, timestamp + "." + rawBody);

        HttpHeaders webhookHeaders = new HttpHeaders();
        webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
        webhookHeaders.set("X-Signature", signature);
        webhookHeaders.set("X-Timestamp", timestamp);

        HttpEntity<String> webhookEntity =
                new HttpEntity<>(rawBody, webhookHeaders);

        ResponseEntity<String> webhookResponse =
                restTemplate.postForEntity(
                        baseUrl() + "/api/payments/webhook",
                        webhookEntity,
                        String.class
                );

        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);


        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getGatewayReference()).isEqualTo("stripe_test_ref");
        assertThat(payment.getAmount()).isGreaterThan(BigDecimal.ZERO);


        Order order = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
