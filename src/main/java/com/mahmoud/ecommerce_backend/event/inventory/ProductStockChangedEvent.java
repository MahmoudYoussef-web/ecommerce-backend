package com.mahmoud.ecommerce_backend.event.inventory;

/**
 * Published (after-commit) whenever an authoritative product stock quantity
 * changes — reservation confirmation and inventory adjustments. Listeners
 * evict the affected product-cache entries so the storefront never advertises
 * stale availability.
 */
public record ProductStockChangedEvent(Long productId) {
}
