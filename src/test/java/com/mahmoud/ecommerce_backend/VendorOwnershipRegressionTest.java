package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Role;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.RoleRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 regression: vendor product ownership (V6 migration semantics).
 *
 * Legacy-data contract, proven here intentionally:
 *  - vendor-owned product   -> owner vendor mutates, other vendors get 403
 *  - NULL ownership product -> admin-managed: ALL vendors get 403, admin OK
 *  - admin bypasses everywhere
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VendorOwnershipRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryId;

    @BeforeEach
    void seedCategory() {

TestBuckets.reset(rateLimitFilter);
        if (categoryId == null) {
            Category category = categoryRepository.save(Category.builder()
                    .name("VendorOwn-" + UUID.randomUUID())
                    .slug("vendor-own-" + UUID.randomUUID())
                    .displayOrder(0)
                    .active(true)
                    .build());
            categoryId = category.getId();
        }
    }

    @AfterEach
    void cleanupVendorData() {
        // Hard-clean rows this class creates so repeat runs stay deterministic
        // (products are only soft-deleted by cleanupProduct).
        if (categoryId != null) {
            jdbcTemplate.update(
                    "DELETE FROM product_images WHERE product_id IN " +
                            "(SELECT id FROM products WHERE category_id = ?)", categoryId);
            jdbcTemplate.update("DELETE FROM products WHERE category_id = ?", categoryId);
            jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
        }
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id IN " +
                        "(SELECT id FROM users WHERE email LIKE 'vendor-%@test.com')");
        // Vendor logins mint refresh sessions; they must go before the user rows.
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id IN " +
                        "(SELECT id FROM users WHERE email LIKE 'vendor-%@test.com')");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'vendor-%@test.com'");
    }

    @Test
    void ownerVendorCanUpdateAndDeleteOwnProduct_otherVendorCannot() {
        String vendorA = createVendor();
        String vendorB = createVendor();

        String tokenA = login(vendorA);
        String tokenB = login(vendorB);

        // Vendor A creates a product -> ownership stamped with A's user id.
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productPayload(), jsonBearer(tokenA)),
                Map.class);
        assertThat(created.getStatusCode())
                .as("create product -> %s", String.valueOf(created.getBody()))
                .isEqualTo(HttpStatus.OK);
        Number productId = ((Number) ((Map<?, ?>) created.getBody().get("data")).get("id"));

        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT vendor_user_id FROM products WHERE id = ?", Long.class,
                productId.longValue());
        assertThat(ownerId).isNotNull();

        // Owner can update.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(updatePricePayload("77.77"), jsonBearer(tokenA)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // A different vendor is rejected.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(updatePricePayload("1.00"), jsonBearer(tokenB)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // A different vendor cannot delete either.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonBearer(tokenB)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        cleanupProduct(productId.longValue());
    }

    @Test
    void legacyNullOwnershipProductIsAdminManaged_allVendorsRejected_adminAllowed() {
        String vendor = createVendor();
        String tokenAdmin = login("admin@gmail.com");
        String tokenVendor = login(vendor);

        // Admin creates the product -> legacy class: vendor_user_id stays NULL.
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl() + "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(productPayload(), jsonBearer(tokenAdmin)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number productId = ((Number) ((Map<?, ?>) created.getBody().get("data")).get("id"));

        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT vendor_user_id FROM products WHERE id = ?", Long.class,
                productId.longValue());
        assertThat(ownerId).as("legacy products must remain admin-managed (NULL)").isNull();

        // Vendor may NOT touch the admin-managed product — neither claim nor edit.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(updatePricePayload("0.01"), jsonBearer(tokenVendor)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonBearer(tokenVendor)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Admin retains full control of legacy rows.
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(updatePricePayload("42.00"), jsonBearer(tokenAdmin)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        cleanupProduct(productId.longValue());
    }

    // ---------- helpers ----------

    private String createVendor() {
        String email = "vendor-" + UUID.randomUUID() + "@test.com";

        User user = userRepository.save(User.builder()
                .firstName("Ven").lastName("Dor")
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .status(com.mahmoud.ecommerce_backend.enums.UserStatus.ACTIVE)
                .emailVerified(true)
                .accountNonLocked(true)
                .enabled(true)
                .tenantId(1L)
                .build());

        Role vendorRole = roleRepository.findByName(RoleName.ROLE_VENDOR)
                .orElseThrow(() -> new AssertionError("ROLE_VENDOR missing"));
        userRoleRepository.save(UserRole.builder().user(user).role(vendorRole).build());

        return email;
    }

    private String login(String email) {
        return login(email, email.endsWith("@gmail.com") ? "123456" : "Password123!");
    }

    private String login(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", req, Map.class);
        assertThat(resp.getStatusCode())
                .as("login %s -> %s", email, String.valueOf(resp.getBody()))
                .isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private Map<String, Object> productPayload() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Map.of(
                "name", "Owned Product " + unique,
                "price", "10.00",
                "stockQuantity", 5,
                "categoryId", categoryId,
                "status", ProductStatus.ACTIVE.name(),
                // Phase 3 made sku/slug required on product creation.
                "sku", "OWN-" + unique,
                "slug", "own-" + unique
        );
    }

    private Map<String, Object> updatePricePayload(String price) {
        return Map.of("price", price);
    }

    private HttpHeaders jsonBearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Soft-delete guard so unique SKU/name leftovers never accumulate as live rows. */
    private void cleanupProduct(Long id) {
        jdbcTemplate.update("UPDATE products SET is_deleted = 1 WHERE id = ?", id);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
