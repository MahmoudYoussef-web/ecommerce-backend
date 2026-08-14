package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.cart.AddToCartRequest;
import com.mahmoud.ecommerce_backend.dto.review.CreateReviewRequest;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.CartRepository;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.ReviewRepository;
import com.mahmoud.ecommerce_backend.repository.WishlistRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 close-out regression tests for the role fixes (B1/B2/B3):
 *
 * - Cart / Wishlist / Review must be guarded by hasRole('CUSTOMER') — a
 *   non-CUSTOMER authenticated principal (e.g. ADMIN) gets 403, anonymous 401,
 *   and a CUSTOMER is served.
 * - Category GET and Review GET must be genuinely public (no auth token).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleAndPublicAccessRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String customerToken;
    private String adminToken;
    private Long productId;
    private String categorySlug;

    @BeforeEach
    void setUp() {
        cleanup();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Public Access Category")
                        .slug("public-cat-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );
        categorySlug = category.getSlug();

        Product product = productRepository.save(
                Product.builder()
                        .name("Public Access Product")
                        .slug("public-prod-" + UUID.randomUUID())
                        .sku("PA-" + UUID.randomUUID())
                        .price(new BigDecimal("100.00"))
                        .stockQuantity(50)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .reviewCount(0)
                        .category(category)
                        .build()
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    // ================= PUBLIC ACCESS (no auth token) =================

    @Test
    void categoryListIsPublicWithoutAuthentication() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/categories",
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void categoryBySlugIsPublicWithoutAuthentication() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/categories/" + categorySlug,
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void productReviewsArePublicWithoutAuthentication() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/reviews/product/" + productId,
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ================= ANONYMOUS MUST BE REJECTED =================

    @Test
    void anonymousIsRejectedFromCart() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/cart",
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousIsRejectedFromWishlist() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/wishlist",
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousIsRejectedFromReviewCreation() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/reviews",
                HttpMethod.POST,
                new HttpEntity<>(reviewRequest()),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ================= CUSTOMER MUST BE ALLOWED =================

    @Test
    void customerCanFetchCart() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.GET,
                new HttpEntity<>(bearer(customerToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void customerCanAddToCart() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(productId);
        request.setQuantity(1);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/cart/items",
                HttpMethod.POST,
                new HttpEntity<>(request, bearer(customerToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void customerCanAddToWishlist() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/wishlist/items/" + productId,
                HttpMethod.POST,
                new HttpEntity<>(bearer(customerToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void customerCanCreateReview() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/reviews",
                HttpMethod.POST,
                new HttpEntity<>(reviewRequest(), bearer(customerToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ================= ADMIN (non-CUSTOMER) MUST BE REJECTED =================

    @Test
    void adminIsRejectedFromCart() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/cart",
                HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminIsRejectedFromWishlist() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/wishlist",
                HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminIsRejectedFromReviewCreation() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/reviews",
                HttpMethod.POST,
                new HttpEntity<>(reviewRequest(), bearer(adminToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ================= HELPERS =================

    private CreateReviewRequest reviewRequest() {
        return CreateReviewRequest.builder()
                .productId(productId)
                .rating(5)
                .comment("Great product")
                .build();
    }

    private String customerToken() {
        if (customerToken == null) {
            customerToken = loginAndGetToken("user@gmail.com", "123456");
        }
        return customerToken;
    }

    private String adminToken() {
        if (adminToken == null) {
            adminToken = loginAndGetToken("admin@gmail.com", "123456");
        }
        return adminToken;
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

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
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
