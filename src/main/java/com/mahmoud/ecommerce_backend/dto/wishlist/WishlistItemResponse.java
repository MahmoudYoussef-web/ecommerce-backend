package com.mahmoud.ecommerce_backend.dto.wishlist;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wishlist item with the product fields the storefront card needs so the
 * frontend can render favourites without one product request per item.
 * Additive only — original fields preserved for existing consumers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private Long productId;
    private String productName;
    private String imageUrl;

    private BigDecimal price;
    private BigDecimal discountedPrice;
    private Integer stockQuantity;
    private List<String> imageUrls;
    private Double averageRating;
    private Integer reviewCount;
}
