package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Where(clause = "is_deleted = false")
@Check(constraints = "total_amount >= 0 AND subtotal >= 0")
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_order_user", columnList = "user_id"),
                @Index(name = "idx_order_status", columnList = "status"),
                @Index(name = "idx_order_number", columnList = "order_number", unique = true),
                @Index(name = "idx_order_created_at", columnList = "created_at"),
                @Index(name = "idx_order_total", columnList = "total_amount")
        }
)
public class Order extends BaseEntity {

    @NotBlank
    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_amount_egp", precision = 12, scale = 2)
    private BigDecimal totalAmountEgp;

    @Column(name = "exchange_rate", precision = 12, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_at")
    private Instant exchangeRateAt;

    @Column(length = 50)
    private String couponCode;

    @Embedded
    private AddressSnapshot shippingAddress;

    @Column(length = 100)
    private String trackingNumber;

    @Column(length = 100)
    private String carrier;

    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    @Column(length = 500)
    private String cancellationReason;

    @Column(name = "return_requested", nullable = false)
    @Builder.Default
    private boolean returnRequested = false;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    private Instant returnedAt;

    @Column(length = 1000)
    private String customerNotes;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // NOTE(Phase 6): the inverse OneToOne to Payment was REMOVED. A nullable
    // inverse OneToOne cannot be proxied by Hibernate, so even fetch=LAZY
    // resolved one SELECT per loaded order on every listing page. Nothing in
    // the codebase navigated it (payment state is read via grouped repository
    // queries / PaymentRepository.findByOrderId), and payment lifecycle is
    // managed exclusively through PaymentRepository.



    @PrePersist
    void prePersist() {
        if (status == null) status = OrderStatus.PENDING;
        validateInvariant();
    }



    public void assignUser(User user) {
        Objects.requireNonNull(user);
        this.user = user;
    }

    public void addItem(OrderItem item) {
        requirePending();
        Objects.requireNonNull(item);

        if (orderItems.contains(item)) return;

        orderItems.add(item);
        item.setOrder(this);

        recalculateTotals();
    }

    public void removeItem(OrderItem item) {
        requirePending();
        Objects.requireNonNull(item);

        if (orderItems.remove(item)) {
            item.setOrder(null);
            recalculateTotals();
        }
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }



    public void markAsPaid() {
        transition(OrderStatus.PAID);
    }

    public void markAsShipped(String carrier, String trackingNumber) {
        Objects.requireNonNull(carrier);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = Instant.now();

        transition(OrderStatus.SHIPPED);
    }

    public void markAsDelivered() {
        this.deliveredAt = Instant.now();
        transition(OrderStatus.DELIVERED);
    }

    public void markAsCancelled(String reason) {
        Objects.requireNonNull(reason);

        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel shipped/delivered order");
        }

        this.cancellationReason = reason;
        this.cancelledAt = Instant.now();

        transition(OrderStatus.CANCELLED);
    }

    /**
     * Paid at the gateway but fulfillment is blocked (e.g. the stock
     * reservation expired and inventory was consumed elsewhere). Money is
     * recorded; an operator must restock-and-continue or refund-and-cancel.
     * The reason is carried in the payment record and the service log.
     */
    public void markNeedsAttention() {
        transition(OrderStatus.NEEDS_ATTENTION);
    }

    /**
     * Customer asks to return a DELIVERED order. Records the request;
     * an admin later approves it (→ REFUNDED) or leaves it delivered.
     */
    public void requestReturn(String reason) {
        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Only delivered orders can be returned");
        }
        if (returnRequested) {
            throw new IllegalStateException("Return already requested");
        }
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Return reason is required");
        }
        this.returnRequested = true;
        this.returnReason = reason;
        this.returnedAt = Instant.now();
    }

    /** Admin approves a pending return request (DELIVERED → REFUNDED). */
    public void approveReturn() {
        if (status != OrderStatus.DELIVERED || !returnRequested) {
            throw new IllegalStateException("No pending return request");
        }
        transition(OrderStatus.REFUNDED);
    }



    public void setCurrencySnapshot(BigDecimal totalAmountEgp, BigDecimal exchangeRate, Instant exchangeRateAt) {
        this.totalAmountEgp = totalAmountEgp;
        this.exchangeRate = exchangeRate;
        this.exchangeRateAt = exchangeRateAt;
    }
    public void applyDiscount(BigDecimal discount) {
        requirePending();
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid discount");
        }

        this.discountAmount = discount;
        recalculateTotals();
    }

    public void setCouponCode(String couponCode) {
        requirePending();
        this.couponCode = couponCode;
    }

    public void setShippingCost(BigDecimal cost) {
        requirePending();
        if (cost == null || cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid shipping cost");
        }

        this.shippingCost = cost;
        recalculateTotals();
    }

    public void recalculateTotals() {
        this.subtotal = orderItems.stream()
                .map(i -> {
                    BigDecimal lineTotal = i.getLineTotal();
                    return lineTotal != null ? lineTotal : i.lineTotalValue();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = subtotal
                .subtract(nvl(discountAmount))
                .add(nvl(shippingCost))
                .add(nvl(taxAmount));

        this.totalAmount = total.max(BigDecimal.ZERO);
    }

    // ================= INTERNAL =================

    private void transition(OrderStatus next) {
        if (!isValid(status, next)) {
            throw new IllegalStateException("Invalid transition " + status + " -> " + next);
        }
        this.status = next;
    }

    private boolean isValid(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.PAID
                    || to == OrderStatus.CANCELLED
                    || to == OrderStatus.NEEDS_ATTENTION;
            case NEEDS_ATTENTION -> to == OrderStatus.PAID
                    || to == OrderStatus.CANCELLED;
            case PAID -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            case SHIPPED -> to == OrderStatus.DELIVERED;
            case DELIVERED -> to == OrderStatus.REFUNDED;
            default -> false;
        };
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order locked after PENDING");
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void validateInvariant() {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalStateException("Order number required");
        }
        if (user == null) {
            throw new IllegalStateException("User required");
        }
        if (orderItems != null && !orderItems.isEmpty()) {
            recalculateTotals();
        }
    }
}