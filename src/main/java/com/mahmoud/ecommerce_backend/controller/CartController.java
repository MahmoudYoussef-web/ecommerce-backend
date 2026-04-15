package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.service.cart.CartService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart APIs")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get current user cart")
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.success(
                cartService.getCart(),
                "Cart fetched successfully"
        );
    }

    @Operation(summary = "Add item to cart")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.success(
                cartService.addItem(request),
                "Item added to cart"
        );
    }

    @Operation(summary = "Update cart item quantity")
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/items/{productId}")
    public ApiResponse<CartResponse> updateItem(@PathVariable Long productId,
                                                @Valid @RequestBody UpdateCartItemRequest request) {
        request.setProductId(productId);

        return ApiResponse.success(
                cartService.updateItem(request),
                "Cart item updated"
        );
    }


    @Operation(summary = "Remove item from cart")
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/items/{productId}")
    public ApiResponse<CartResponse> removeItem(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.success(
                cartService.removeItem(productId, variantId),
                "Item removed from cart"
        );
    }

    @Operation(summary = "Clear cart")
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping
    public ApiResponse<Void> clear() {
        cartService.clearCart();
        return ApiResponse.success(null, "Cart cleared successfully");
    }
}