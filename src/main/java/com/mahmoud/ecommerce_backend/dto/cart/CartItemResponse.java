package com.mahmoud.ecommerce_backend.dto.cart;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long productId;
    private Long variantId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String imageUrl;
}