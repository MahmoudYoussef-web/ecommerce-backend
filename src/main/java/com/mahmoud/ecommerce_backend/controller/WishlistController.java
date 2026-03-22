// ===================== WishlistController =====================
package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.wishlist.WishlistResponse;
import com.mahmoud.ecommerce_backend.service.wishlist.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public WishlistResponse getWishlist() {
        return wishlistService.getWishlist();
    }

    @PostMapping("/items/{productId}")
    public WishlistResponse add(@PathVariable Long productId) {
        return wishlistService.addProduct(productId);
    }

    @DeleteMapping("/items/{productId}")
    public WishlistResponse remove(@PathVariable Long productId) {
        return wishlistService.removeProduct(productId);
    }
}