package com.mahmoud.ecommerce_backend.dto.cart;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {
    private Long productId;
    private Long variantId;
    private Integer quantity;
}