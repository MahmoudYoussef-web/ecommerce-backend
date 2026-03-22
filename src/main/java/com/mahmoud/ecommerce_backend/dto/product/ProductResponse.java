package com.mahmoud.ecommerce_backend.dto.product;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import com.mahmoud.ecommerce_backend.dto.category.CategoryResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private CategoryResponse category;
    private List<String> imageUrls;
}