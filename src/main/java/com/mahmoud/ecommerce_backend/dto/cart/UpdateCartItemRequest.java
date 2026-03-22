package com.mahmoud.ecommerce_backend.dto.cart;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {
    private Long productId;
    private Integer quantity;
}