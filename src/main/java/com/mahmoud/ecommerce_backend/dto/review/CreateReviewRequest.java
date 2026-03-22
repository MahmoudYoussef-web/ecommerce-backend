package com.mahmoud.ecommerce_backend.dto.review;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {
    private Long productId;
    private Integer rating;
    private String comment;
}