package com.mahmoud.ecommerce_backend.enums;

/**
 * Payment transaction lifecycle states.
 *
 * PENDING            - payment object created, no gateway call yet.
 * INITIATED          - gateway call in progress.
 * COMPLETED          - funds successfully captured.
 * FAILED             - gateway rejected or timed out.
 * REFUNDED           - full refund processed.
 * PARTIALLY_REFUNDED - partial amount returned (e.g., one item returned).
 * CANCELLED          - payment cancelled before capture.
 * EXPIRED            - payment session expired before user completed it.
 */
public enum PaymentStatus {
    PENDING,
    INITIATED,
    COMPLETED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    CANCELLED,
    EXPIRED
}
