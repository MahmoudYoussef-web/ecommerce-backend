package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Where(clause = "is_deleted = false")
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_order", columnList = "order_id", unique = true),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_gateway_ref", columnList = "gateway_reference"),
                @Index(name = "idx_payment_method", columnList = "payment_method"),
                @Index(name = "idx_payment_event_id", columnList = "event_id", unique = true)
        }
)
public class Payment extends BaseEntity {

    @Version
    private Long version;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "gateway_reference", length = 255)
    private String gatewayReference;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "event_id", unique = true, length = 255)
    private String eventId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @NotNull
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;



    public void setStatus(PaymentStatus status) {
        transitionTo(status);
    }

    public void markAsPaid(String reference) {
        complete(reference);
    }



    public void initiate() {
        transitionTo(PaymentStatus.INITIATED);
    }

    public void complete(String gatewayReference) {
        Objects.requireNonNull(gatewayReference);

        transitionTo(PaymentStatus.COMPLETED);

        this.gatewayReference = gatewayReference;
        this.paidAt = Instant.now();

        // Only a still-pending order transitions to PAID here. The late-webhook
        // compensation path pre-transitions the order to NEEDS_ATTENTION, which
        // must survive payment completion.
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            order.markAsPaid();
        }
    }

    public void fail(String reason) {
        Objects.requireNonNull(reason);

        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    public void cancel(String reason) {
        Objects.requireNonNull(reason);

        transitionTo(PaymentStatus.CANCELLED);
        this.failureReason = reason;
    }

    /**
     * Recovery path: re-open a FAILED/CANCELLED payment so the customer can
     * retry. Only these two states may rebase; COMPLETED payments never do.
     * Appends an audit line to gatewayResponse so the retry history remains
     * visible on the single payment row that exists for the order.
     */
    public void rebaseToPending(String auditNote) {
        Objects.requireNonNull(auditNote);

        PaymentStatus previous = this.status;
        transitionTo(PaymentStatus.PENDING);

        this.failureReason = null;
        this.eventId = null;

        String entry = "rebased " + previous + " -> PENDING at " + Instant.now() + ": " + auditNote;
        this.gatewayResponse = this.gatewayResponse == null
                ? entry
                : this.gatewayResponse + "\n" + entry;
    }

    public void refund(BigDecimal refundAmount) {

        Objects.requireNonNull(refundAmount);

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid refund amount");
        }

        if (status != PaymentStatus.COMPLETED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Refund not allowed");
        }

        BigDecimal newRefunded = refundedAmount.add(refundAmount);

        if (newRefunded.compareTo(amount) > 0) {
            throw new IllegalStateException("Refund exceeds amount");
        }

        this.refundedAmount = newRefunded;
        this.refundedAt = Instant.now();

        if (newRefunded.compareTo(amount) == 0) {
            transitionTo(PaymentStatus.REFUNDED);
        } else {
            transitionTo(PaymentStatus.PARTIALLY_REFUNDED);
        }
    }



    public void setEventId(String eventId) {
        if (this.eventId != null && !this.eventId.equals(eventId)) {
            throw new IllegalStateException("EventId already set");
        }
        this.eventId = eventId;
    }

    public void setGatewayReference(String gatewayReference) {
        this.gatewayReference = gatewayReference;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }


    private void transitionTo(PaymentStatus next) {

        if (this.status == next) return;

        if (!isValidTransition(this.status, next)) {
            throw new IllegalStateException("Invalid transition " + this.status + " -> " + next);
        }

        this.status = next;
    }

    private boolean isValidTransition(PaymentStatus current, PaymentStatus next) {
        return switch (current) {
            case PENDING -> next == PaymentStatus.INITIATED
                    || next == PaymentStatus.COMPLETED
                    || next == PaymentStatus.FAILED
                    || next == PaymentStatus.CANCELLED;

            case INITIATED -> next == PaymentStatus.COMPLETED
                    || next == PaymentStatus.FAILED
                    || next == PaymentStatus.CANCELLED;

            // Recovery: a failed/cancelled attempt may be retried. The single
            // payment row per order is rebased to PENDING; the audit trail of
            // the failed attempt stays in failureReason / gatewayResponse.
            case FAILED -> next == PaymentStatus.PENDING;

            case CANCELLED -> next == PaymentStatus.PENDING;

            case COMPLETED -> next == PaymentStatus.REFUNDED
                    || next == PaymentStatus.PARTIALLY_REFUNDED;

            case PARTIALLY_REFUNDED -> next == PaymentStatus.REFUNDED;

            default -> false;
        };
    }
    public static Payment create(Order order,
                                 PaymentMethod method,
                                 BigDecimal amount,
                                 String currency) {

        Objects.requireNonNull(order);
        Objects.requireNonNull(method);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        return Payment.builder()
                .order(order)
                .paymentMethod(method)
                .amount(amount)
                .currency(currency.toUpperCase())
                .status(PaymentStatus.PENDING)
                .build();
    }

    @PrePersist
    void prePersist() {
        if (status == null) status = PaymentStatus.PENDING;
        if (refundedAmount == null) refundedAmount = BigDecimal.ZERO;

        Objects.requireNonNull(order);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(paymentMethod);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid amount");
        }
    }
}