package com.mahmoud.ecommerce_backend.enums;

/**
 * Full order lifecycle.
 *
 * Happy path:  PENDING → CONFIRMED → PAID → PROCESSING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED
 * Cancel path: any state before SHIPPED → CANCELLED
 * Refund path: DELIVERED → REFUNDED
 * Error path:  PAID → FAILED (rare; payment reversal)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAID,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    FAILED
}
