package com.mahmoud.ecommerce_backend.dto.review;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {
    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be at least 1")
    @Max(value = 5, message = "rating must be at most 5")
    private Integer rating;

    @Size(max = 3000, message = "comment must not exceed 3000 characters")
    private String comment;
}
