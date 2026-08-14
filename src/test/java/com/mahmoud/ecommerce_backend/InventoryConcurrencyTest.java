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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class InventoryConcurrencyTest {

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

    private static final int THREADS = 20;
    private static final int STOCK = 5;
    private static final int QUANTITY_PER_THREAD = 1;

    private Product product;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        cleanup();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Concurrency Test Category")
                        .slug("conc-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        product = productRepository.save(
                Product.builder()
                        .name("Concurrency Test Product")
                        .slug("conc-prod-" + UUID.randomUUID())
                        .sku("CONC-" + UUID.randomUUID())
                        .price(new BigDecimal("100.00"))
                        .stockQuantity(STOCK)
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
    void shouldNotOversellUnderConcurrency() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Boolean>> results = new ArrayList<>();

        CountDownLatch latch = new CountDownLatch(1);


        for (int i = 0; i < THREADS; i++) {
            results.add(executor.submit(() -> {
                try {
                    latch.await();

                    reservationService.reserve(
                            product.getId(),
                            QUANTITY_PER_THREAD,
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
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int success = 0;
        int failed = 0;

        for (Future<Boolean> f : results) {
            if (f.get()) success++;
            else failed++;
        }



        assertThat(success).isEqualTo(STOCK);
        assertThat(failed).isEqualTo(THREADS - STOCK);

        long reservedCount = reservationRepository.findAll().stream()
                .filter(r -> r.getProductId().equals(product.getId()))
                .count();
        assertThat(reservedCount).isEqualTo(STOCK);

        System.out.println("SUCCESS = " + success);
        System.out.println("FAILED = " + failed);
    }
}
