package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 verification: authoritative stock changes must invalidate product
 * caches AFTER commit, so the storefront never advertises stale availability.
 *
 * Chain under test: reservation confirm (stock decrement, tx commits)
 *   → ProductStockChangedEvent → ProductCacheInvalidator evicts
 *   → next GET /api/products/{id} reflects the NEW quantity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockCacheInvalidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long productId;

    private Long orderId;

    private int initialStock;

    @Test
    void purchaseConfirmEvictsCacheAndNextReadReflectsNewStock() {
        seed();

        // 1) Warm the cache — detail response shows the ORIGINAL stock.
        Map<String, Object> before = getDetail();
        assertThat(stockOf(before)).isEqualTo(initialStock);

        // 2) Authoritative purchase: confirm the seeded reservation inside a
        //    real transaction (as the payment flow would). Commit publishes
        //    the after-commit eviction.
        transactionTemplate.executeWithoutResult(tx ->
                reservationService.confirmForOrder(orderId));

        // 3) Next read must NOT serve the stale cached value.
        Map<String, Object> after = getDetail();
        assertThat(stockOf(after))
                .as("detail endpoint must reflect post-purchase stock immediately")
                .isEqualTo(initialStock - 2);
    }

    // ---------- helpers ----------

    private void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.save(Category.builder()
                .name("StaleCache-" + suffix)
                .slug("stale-cache-" + suffix)
                .displayOrder(0)
                .active(true)
                .build());

        initialStock = 50;
        Product product = productRepository.save(Product.builder()
                .name("Stale Cache Probe " + suffix)
                .slug("stale-probe-" + suffix)
                .sku("SCP-" + suffix)
                .price(new BigDecimal("9.99"))
                .stockQuantity(initialStock)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .build());
        productId = product.getId();

        User buyer = userRepository.findByEmail("user@gmail.com").orElseGet(() -> {
            User u = User.builder()
                    .firstName("Cache").lastName("Buyer")
                    .email("cache-buyer-" + suffix + "@test.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .status(com.mahmoud.ecommerce_backend.enums.UserStatus.ACTIVE)
                    .emailVerified(true).enabled(true).accountNonLocked(true)
                    .tenantId(1L)
                    .build();
            return userRepository.save(u);
        });

        Order order = Order.builder()
                .orderNumber("SCV-" + suffix)
                .user(buyer)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("19.98"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("19.98"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Cache Buyer").addressLine1("1 Cache Rd")
                        .city("Cairo").postalCode("11511").country("Egypt")
                        .build())
                .build();
        orderId = orderRepository.save(order).getId();

        // Reserve exactly like checkout does (inside its own transaction).
        transactionTemplate.executeWithoutResult(tx ->
                reservationService.reserve(productId, 2, orderId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDetail() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/api/products/" + productId, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    private Integer stockOf(Map<String, Object> detail) {
        return ((Number) detail.get("stockQuantity")).intValue();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
