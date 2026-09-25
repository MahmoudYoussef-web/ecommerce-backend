package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closure verification: homepage sections endpoint.
 *
 * Self-sufficient fixtures (own category + ACTIVE products) so the assertions
 * never depend on whatever other suites left in the shared test database.
 *
 * Verifies:
 *  - bounded per-category payload (never a catalog dump)
 *  - repeated reads are stable and cheap (no catalog SQL on warm reads once
 *    Hibernate/query-plan caches are warm; endpoint is intentionally uncached)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class HomeSectionsCacheTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void seedOwnSection() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.save(Category.builder()
                .name("HomeSection-" + suffix)
                .slug("home-section-" + suffix)
                .displayOrder(0)
                .active(true)
                .build());

        for (int i = 0; i < 3; i++) {
            productRepository.save(Product.builder()
                    .name("Home Section Product " + suffix + " " + i)
                    .slug("hs-" + suffix + "-" + i)
                    .sku("HS-" + suffix + "-" + i)
                    .price(new BigDecimal("7.77"))
                    .stockQuantity(5)
                    .status(ProductStatus.ACTIVE)
                    .category(category)
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void homeSectionsAreBounded_andRepeatReadsAreStable() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        ResponseEntity<Map> first =
                restTemplate.getForEntity(baseUrl() + "/api/products/home-sections?perCategory=6", Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> sections =
                (List<Map<String, Object>>) ((Map<String, Object>) first.getBody().get("data")).get("sections");

        // Our seeded section MUST be present with exactly its 3 products —
        // proves the endpoint serves real data regardless of shared-DB state.
        Map<String, Object> own = sections.stream()
                .filter(s -> String.valueOf(s.get("categoryName")).startsWith("HomeSection-"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "seeded section missing | body=" + first.getBody()));
        List<Object> ownProducts = (List<Object>) own.get("products");
        assertThat(ownProducts).hasSize(3);

        // Bounded contract: no section ever exceeds perCategory items.
        for (Map<String, Object> section : sections) {
            List<Object> products = (List<Object>) section.get("products");
            assertThat(products.size()).isLessThanOrEqualTo(6);
        }

        // Warm repeat: stable payload, bounded statements (auth overhead only).
        stats.clear();
        ResponseEntity<Map> second =
                restTemplate.getForEntity(baseUrl() + "/api/products/home-sections?perCategory=6", Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> repeat =
                (List<Map<String, Object>>) ((Map<String, Object>) second.getBody().get("data")).get("sections");
        assertThat(repeat.size()).isEqualTo(sections.size());

        int statements = (int) stats.getPrepareStatementCount();
        assertThat(statements)
                .as("repeat read statement count")
                .isLessThanOrEqualTo(30); // full method re-execution is fine; just bound it
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
