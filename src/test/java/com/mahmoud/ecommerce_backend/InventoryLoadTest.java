package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class InventoryLoadTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    private static final int USERS = 1000;
    private static final int THREAD_POOL = 50;

    @Test
    void simulateHighLoad() throws Exception {

        Product product = productRepository.findById(1L).orElseThrow();

        int initialStock = product.getStockQuantity();

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


        assertThat(success).isLessThanOrEqualTo(initialStock);

        assertThat(duration.toSeconds()).isLessThan(10);
    }
}