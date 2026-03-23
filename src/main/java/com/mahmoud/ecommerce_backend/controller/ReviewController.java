package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.review.*;
import com.mahmoud.ecommerce_backend.service.review.ReviewService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review APIs")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Create product review")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ApiResponse<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.success(
                reviewService.createReview(request),
                "Review created successfully"
        );
    }

    @Operation(summary = "Get product reviews")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/product/{productId}")
    public ApiResponse<List<ReviewResponse>> getProductReviews(@PathVariable Long productId) {
        return ApiResponse.success(
                reviewService.getProductReviews(productId),
                "Product reviews fetched successfully"
        );
    }
}