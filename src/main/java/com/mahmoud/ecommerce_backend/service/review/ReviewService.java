package com.mahmoud.ecommerce_backend.service.review;

import com.mahmoud.ecommerce_backend.dto.review.CreateReviewRequest;
import com.mahmoud.ecommerce_backend.dto.review.ReviewResponse;

import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(CreateReviewRequest request);

    List<ReviewResponse> getProductReviews(Long productId);
}
