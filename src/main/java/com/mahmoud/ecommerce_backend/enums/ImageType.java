package com.mahmoud.ecommerce_backend.enums;

/**
 * Classifies how a product image is used in the storefront.
 *
 * THUMBNAIL - small preview image (e.g., search results, cart).
 * GALLERY   - full-size image shown on the product detail page.
 * BANNER    - wide promotional image.
 * AVATAR    - user profile picture (reused for User, not Product).
 */
public enum ImageType {
    THUMBNAIL,
    GALLERY,
    BANNER,
    AVATAR
}
