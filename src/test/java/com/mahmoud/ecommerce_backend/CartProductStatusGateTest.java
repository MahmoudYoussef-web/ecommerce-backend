package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.cart.AddToCartRequest;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.Role;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.security.user.ShopUserDetailsService;
import com.mahmoud.ecommerce_backend.service.cart.CartService;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Closure F-2 regression: the cart gate must match the checkout gate —
 * ONLY ProductStatus.ACTIVE products may be added. Every other lifecycle
 * state (DRAFT, INACTIVE, DISCONTINUED, OUT_OF_STOCK) is rejected at add-time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartProductStatusGateTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ShopUserDetailsService userDetailsService;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long categoryId;

    @BeforeEach
    void resetBuckets() {
        TestBuckets.reset(rateLimitFilter);
        if (categoryId == null) {
            Category category = categoryRepository.save(Category.builder()
                    .name("CartGate-" + UUID.randomUUID())
                    .slug("cart-gate-" + UUID.randomUUID())
                    .displayOrder(0)
                    .active(true)
                    .build());
            categoryId = category.getId();
        }
    }

    @Test
    void activeProductCanBeAddedToCart() {
        Long productId = seed(ProductStatus.ACTIVE);
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(productId);
        request.setQuantity(1);

        cartService.addItem(request);

        // Read back through the normal path (fresh carts map from the DB).
        assertThat(cartService.getCart().getItems()).isNotEmpty();

        cleanup(productId);
    }

    @Test
    void draftInactiveDiscontinuedAndOutOfStockProductsAreRejected() {
        for (ProductStatus status : new ProductStatus[]{
                ProductStatus.DRAFT, ProductStatus.INACTIVE,
                ProductStatus.DISCONTINUED, ProductStatus.OUT_OF_STOCK}) {

            Long productId = seed(status);
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(productId);
            request.setQuantity(1);

            assertThatThrownBy(() -> cartService.addItem(request))
                    .as("status %s must be rejected from cart", status)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not available");

            cleanup(productId);
        }
    }

    // ---------- helpers ----------

    /** Service-level calls run on this thread: install the principal directly. */
    @BeforeEach
    void authenticateOnThread() {
        String email = "cartgate-" + UUID.randomUUID() + "@test.com";

        var user = userRepository.save(com.mahmoud.ecommerce_backend.entity.User.builder()
                .firstName("Cart").lastName("Gate")
                .email(email)
                .passwordHash("$2a$10$RHxP/RmZHNcKI5/YThlX3.YWgf7o8O/1W7HywDyuCoJwJ0ltRIl56")
                .status(com.mahmoud.ecommerce_backend.enums.UserStatus.ACTIVE)
                .emailVerified(true).enabled(true).accountNonLocked(true)
                .tenantId(1L)
                .build());

        Role customerRole = roleRepository.findByName(com.mahmoud.ecommerce_backend.enums.RoleName.ROLE_CUSTOMER)
                .orElseThrow();
        userRoleRepository.save(com.mahmoud.ecommerce_backend.entity.UserRole.builder()
                .user(user).role(customerRole).build());

        var principal = userDetailsService.loadUserByUsername(email);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private Long seed(ProductStatus status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Product product = productRepository.save(Product.builder()
                .name("Cart Gate " + suffix)
                .slug("cart-gate-p-" + suffix)
                .sku("CG-" + suffix)
                .price(new BigDecimal("5.00"))
                .stockQuantity(10)
                .status(status)
                .category(categoryRepository.findById(categoryId).orElseThrow())
                .build());
        return product.getId();
    }

    private void cleanup(Long productId) {
        jdbcTemplate.update("DELETE FROM cart_items WHERE product_id = ?", productId);
        jdbcTemplate.update("UPDATE products SET is_deleted = 1 WHERE id = ?", productId);
    }
}
