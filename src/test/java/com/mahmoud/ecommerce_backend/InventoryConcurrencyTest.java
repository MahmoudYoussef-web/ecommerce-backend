package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class InventoryConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    private static final int THREADS = 20;
    private static final int QUANTITY_PER_THREAD = 1;

    @Test
    void shouldNotOversellUnderConcurrency() throws Exception {

        Product product = productRepository.findById(1L).orElseThrow();

        int initialStock = product.getStockQuantity();

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



        assertThat(success).isLessThanOrEqualTo(initialStock);
        if (initialStock < THREADS) {
            assertThat(failed).isGreaterThan(0);
        }

        System.out.println("SUCCESS = " + success);
        System.out.println("FAILED = " + failed);
    }
}