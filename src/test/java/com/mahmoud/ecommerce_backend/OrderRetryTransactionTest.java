package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.Address;
import com.mahmoud.ecommerce_backend.entity.Cart;
import com.mahmoud.ecommerce_backend.entity.CartItem;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.AddressRepository;
import com.mahmoud.ecommerce_backend.repository.CartItemRepository;
import com.mahmoud.ecommerce_backend.repository.CartRepository;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;


/**
 * Phase 2 regression: createOrder retries must run each attempt in a FRESH
 * transaction (TransactionTemplate), so a transient
 * OptimisticLockingFailureException is survivable and exhausted retries fail
 * cleanly instead of surfacing as a 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OrderRetryTransactionTest.RetryProbeConfig.class)
class OrderRetryTransactionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @TestConfiguration
    static class RetryProbeConfig {
        // Reserved for future probe beans; @MockBean drives the scenario.
    }

    private Long productId;
    private Long addressId;
    private Long cartItemId;

    @BeforeEach
    void seedCartAndAddress() throws Exception {
        ATTEMPTS.set(0);

        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Retry Category")
                        .slug("retry-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Retry Product")
                        .slug("retry-prod-" + UUID.randomUUID())
                        .sku("RTY-" + UUID.randomUUID())
                        .price(new BigDecimal("5.00"))
                        .stockQuantity(50)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .reviewCount(0)
                        .category(category)
                        .build()
        );
        productId = product.getId();

        Address address = Address.builder()
                .user(user)
                .fullName("Retry Tester")
                .addressLine1("7 Retry St")
                .city("Cairo")
                .country("Egypt")
                .postalCode("11511")
                .phone("0100000000")
                .build();
        addressId = addressRepository.save(address).getId();

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);
        if (cart == null) {
            cart = cartRepository.save(Cart.builder().user(user).build());
        }
        CartItem item = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(1)
                .unitPrice(new BigDecimal("5.00"))
                .totalPrice(new BigDecimal("5.00"))
                .build();
        cartItemId = cartItemRepository.save(item).getId();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (cartItemId != null) jdbcTemplate.update("DELETE FROM cart_items WHERE id = ?", cartItemId);
        if (addressId != null) jdbcTemplate.update("DELETE FROM addresses WHERE id = ?", addressId);
        if (productId != null) jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM stock_reservations WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'RTY-%')");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'RTY-%')");
        jdbcTemplate.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'RTY-%')");
        jdbcTemplate.update("DELETE FROM orders WHERE order_number LIKE 'RTY-%'");
        jdbcTemplate.update("DELETE FROM carts WHERE user_id = (SELECT id FROM users WHERE email = 'user@gmail.com')");
        ATTEMPTS.set(0);
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

    private ResponseEntity<Map> placeOrder(String token) {
        Map<String, Object> body = Map.of(
                "addressId", addressId,
                "customerNotes", "retry test",
                "paymentMethod", "CASH_ON_DELIVERY"
        );
        return restTemplate.exchange(
                baseUrl() + "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                Map.class
        );
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void transientOptimisticLockConflictIsRetriedAndOrderIsCreated() {
        String token = customerToken();

        doAnswer(inv -> {
            if (ATTEMPTS.incrementAndGet() <= 2) {
                throw new OptimisticLockingFailureException("simulated concurrent product update");
            }
            return StockReservation.builder().build();
        }).when(reservationService).reserve(anyLong(), anyInt(), anyLong());

        ResponseEntity<Map> resp = placeOrder(token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ATTEMPTS.get()).isEqualTo(3);
    }

    @Test
    void exhaustedRetriesFailCleanlyWithBadRequest() {
        String token = customerToken();

        doAnswer(inv -> {
            ATTEMPTS.incrementAndGet();
            throw new OptimisticLockingFailureException("persistent conflict");
        }).when(reservationService).reserve(anyLong(), anyInt(), anyLong());

        ResponseEntity<Map> resp = placeOrder(token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ATTEMPTS.get()).isEqualTo(3);
        assertThat(String.valueOf(resp.getBody())).contains("Concurrent update");
    }
}
