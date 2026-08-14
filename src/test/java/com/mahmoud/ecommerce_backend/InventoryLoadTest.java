package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class InventoryLoadTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int USERS = 1000;
    private static final int LOAD_STOCK = 750;
    private static final int THREAD_POOL = 50;

    private Product product;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        cleanup();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Load Test Category")
                        .slug("load-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        product = productRepository.save(
                Product.builder()
                        .name("Load Test Product")
                        .slug("load-prod-" + UUID.randomUUID())
                        .sku("LOAD-" + UUID.randomUUID())
                        .price(new BigDecimal("100.00"))
                        .stockQuantity(LOAD_STOCK)
                        .reviewCount(0)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .category(category)
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        cleanup();
        TenantContext.clear();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM stock_reservations WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM products WHERE tenant_id = 1");
        jdbcTemplate.update("DELETE FROM categories WHERE tenant_id = 1");
    }

    @Test
    void simulateHighLoad() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL);

        List<Future<Boolean>> futures = new ArrayList<>();

        CountDownLatch latch = new CountDownLatch(1);

        Instant start = Instant.now();

        for (int i = 0; i < USERS; i++) {

            futures.add(executor.submit(() -> {
                try {
                    latch.await();

                    reservationService.reserve(
                            product.getId(),
                            1,
                            null
                    );

                    return true;

                } catch (Exception e) {
                    return false;
                }
            }));
        }

        latch.countDown();

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        Instant end = Instant.now();

        int success = 0;
        int failed = 0;

        for (Future<Boolean> f : futures) {
            if (f.get()) success++;
            else failed++;
        }

        Duration duration = Duration.between(start, end);

        System.out.println("========= LOAD TEST RESULT =========");
        System.out.println("Users: " + USERS);
        System.out.println("Success: " + success);
        System.out.println("Failed: " + failed);
        System.out.println("Time (ms): " + duration.toMillis());
        System.out.println("Throughput (req/sec): " + (USERS * 1000.0 / duration.toMillis()));


        assertThat(success).isEqualTo(LOAD_STOCK);
        assertThat(failed).isEqualTo(USERS - LOAD_STOCK);

        long reservedCount = reservationRepository.findAll().stream()
                .filter(r -> r.getProductId().equals(product.getId()))
                .count();
        assertThat(reservedCount).isEqualTo(LOAD_STOCK);

        assertThat(duration.toSeconds()).isLessThanOrEqualTo(15);
    }
}
