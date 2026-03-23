package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.wishlist.WishlistResponse;
import com.mahmoud.ecommerce_backend.service.wishlist.WishlistService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "User wishlist APIs")
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "Get user wishlist")
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ApiResponse<WishlistResponse> getWishlist() {
        return ApiResponse.success(
                wishlistService.getWishlist(),
                "Wishlist fetched successfully"
        );
    }

    @Operation(summary = "Add product to wishlist")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/items/{productId}")
    public ApiResponse<WishlistResponse> add(@PathVariable Long productId) {
        return ApiResponse.success(
                wishlistService.addProduct(productId),
                "Product added to wishlist"
        );
    }

    @Operation(summary = "Remove product from wishlist")
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/items/{productId}")
    public ApiResponse<WishlistResponse> remove(@PathVariable Long productId) {
        return ApiResponse.success(
                wishlistService.removeProduct(productId),
                "Product removed from wishlist"
        );
    }
}