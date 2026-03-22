package com.mahmoud.ecommerce_backend.enums;

/**
 * Indicates what purpose a saved Address serves.
 *
 * SHIPPING - delivery address.
 * BILLING  - invoicing address (may differ from shipping).
 * BOTH     - address serves as both shipping and billing.
 */
public enum AddressType {
    SHIPPING,
    BILLING,
    BOTH
}
