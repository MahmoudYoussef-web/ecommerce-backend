// ===================== ReviewController =====================
package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.review.*;
import com.mahmoud.ecommerce_backend.service.review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ReviewResponse create(@Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createReview(request);
    }

    @GetMapping("/product/{productId}")
    public List<ReviewResponse> getProductReviews(@PathVariable Long productId) {
        return reviewService.getProductReviews(productId);
    }
}