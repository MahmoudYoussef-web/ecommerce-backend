package com.mahmoud.ecommerce_backend.dto.order;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long productId;
    private Long variantId;
    private String productName;
    private String productSku;
    private String productImageUrl;
    private BigDecimal priceAtPurchase;
    private Integer quantity;
}