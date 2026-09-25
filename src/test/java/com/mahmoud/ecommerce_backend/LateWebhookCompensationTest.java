package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.service.payment.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 regression: a Stripe "payment succeeded" webhook that arrives after
 * the stock reservation expired (and the inventory was consumed elsewhere)
 * must NOT fail forever. Compensation:
 *   - payment is recorded as COMPLETED (money really was received),
 *   - order is flagged NEEDS_ATTENTION for an operator,
 *   - unconfirmed reservations are released,
 *   - replaying the same event is an idempotent no-op.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LateWebhookCompensationTest {

    private static final int INITIAL_STOCK = 5;
    private static final int RESERVED_QTY = 3;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long orderId;
    private Long productId;
    private Long paymentId;
    private Long reservationId;

    @BeforeEach
    void seedOrderWithReservation() {
        cleanup();

        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Late WH Category")
                        .slug("late-wh-" + UUID.randomUUID())
                        .displayOrder(0)
                        .active(true)
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Late WH Product")
                        .slug("late-wh-" + UUID.randomUUID())
                        .sku("LWH-" + UUID.randomUUID())
                        .price(new BigDecimal("10.00"))
                        .stockQuantity(INITIAL_STOCK)
                        .lowStockThreshold(0)
                        .status(ProductStatus.ACTIVE)
                        .reviewCount(0)
                        .category(category)
                        .build()
        );
        productId = product.getId();

        Order order = Order.builder()
                .orderNumber("LWH-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("30.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("30.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Late WH Test")
                        .addressLine1("9 Late St")
                        .city("Cairo")
                        .postalCode("11511")
                        .country("Egypt")
                        .build())
                .build();
        orderId = orderRepository.save(order).getId();

        StockReservation reservation = reservationRepository.save(
                StockReservation.builder()
                        .productId(productId)
                        .quantity(RESERVED_QTY)
                        .status(StockReservationStatus.RESERVED)
                        .orderId(orderId)
                        .expiresAt(Instant.now().plusSeconds(900))
                        .build()
        );
        reservationId = reservation.getId();

        Payment payment = paymentRepository.save(
                Payment.create(orderRepository.findById(orderId).orElseThrow(),
                        com.mahmoud.ecommerce_backend.enums.PaymentMethod.STRIPE,
                        new BigDecimal("30.00"), "USD")
        );
        paymentId = payment.getId();
    }

    @AfterEach
    void cleanup() {
        if (orderId != null) {
            jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stock_reservations WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        if (productId != null) {
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        }
        orderId = null;
        productId = null;
    }

    @Test
    void lateSuccessfulWebhookCompensatesInsteadOfFailingForever() {
        // Simulate: reservation expired, scheduler released it, and the freed
        // stock was bought by someone else. Stock is now below the reserved
        // quantity, so confirm() cannot succeed.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStockQuantity(0);
        productRepository.save(product);

        String eventId = "evt-late-" + UUID.randomUUID();

        // Old behaviour: BadRequest -> webhook tx rollback -> Stripe retries forever.
        // New behaviour: compensation completes.
        paymentService.processStripeWebhook(eventId, paymentId,
                PaymentStatus.COMPLETED, "pi_late", new BigDecimal("30.00"), "USD");

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEEDS_ATTENTION);

        // Unconfirmed reservations released; stock untouched at 0.
        StockReservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.RELEASED);
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(0);

        // Replay of the SAME event: pure no-op.
        paymentService.processStripeWebhook(eventId, paymentId,
                PaymentStatus.COMPLETED, "pi_late", new BigDecimal("30.00"), "USD");

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEEDS_ATTENTION);
    }

    @Test
    void onTimeSuccessfulWebhookStillConfirmsStock() {
        String eventId = "evt-ontime-" + UUID.randomUUID();

        paymentService.processStripeWebhook(eventId, paymentId,
                PaymentStatus.COMPLETED, "pi_ontime", new BigDecimal("30.00"), "USD");

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);

        // Stock decremented by the reserved quantity: 5 - 3 = 2.
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - RESERVED_QTY);

        StockReservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.CONFIRMED);
    }
}
