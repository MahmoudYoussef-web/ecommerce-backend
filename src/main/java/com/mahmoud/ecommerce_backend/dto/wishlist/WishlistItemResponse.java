package com.mahmoud.ecommerce_backend.dto.wishlist;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private Long productId;
    private String productName;
    private String imageUrl;
}