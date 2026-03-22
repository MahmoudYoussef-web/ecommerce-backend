package com.mahmoud.ecommerce_backend.enums;

/**
 * Visibility and availability states for a product.
 *
 * DRAFT        - being prepared; not visible in storefront.
 * ACTIVE       - live and purchasable.
 * INACTIVE     - temporarily hidden by admin/vendor.
 * OUT_OF_STOCK - visible but not purchasable (stockQuantity == 0).
 * DISCONTINUED - permanently removed from catalog; kept for order history.
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    OUT_OF_STOCK,
    DISCONTINUED
}
