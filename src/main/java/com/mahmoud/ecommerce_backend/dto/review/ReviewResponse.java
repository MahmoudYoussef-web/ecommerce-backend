package com.mahmoud.ecommerce_backend.dto.review;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long userId;
    private String userName;
    private Integer rating;
    private String title;
    private String comment;
    private Boolean verifiedPurchase;
    private Integer helpfulVotes;
}