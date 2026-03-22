package com.mahmoud.ecommerce_backend.dto.product;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String slug;
    private String sku;
    private Long categoryId;
    private List<String> imageUrls;
}