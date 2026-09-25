package com.mahmoud.ecommerce_backend.dto.product;

import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @Size(min = 2, max = 200)
    private String name;

    private String description;

    // Money fields are validated here so bad precision/amounts surface as a
    // 400 VALIDATION_ERROR instead of a flush-time constraint violation (500).
    @DecimalMin(value = "0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @DecimalMin(value = "0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal discountedPrice;

    @PositiveOrZero
    private Integer stockQuantity;

    private String slug;

    private String sku;

    private Long categoryId;

    /** Same http(s)-only constraint as creation (stored content rendered by browsers). */
    private List<@Size(max = 500) @Pattern(regexp = "^https?://\\S+$",
            message = "image URL must be an absolute http(s) URL") String> imageUrls;

    private ProductStatus status;
}