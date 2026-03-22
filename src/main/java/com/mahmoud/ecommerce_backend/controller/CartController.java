// ===================== CartController =====================
package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.service.cart.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart();
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@PathVariable Long productId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        request.setProductId(productId);
        return cartService.updateItem(request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId) {
        return cartService.removeItem(productId);
    }

    @DeleteMapping
    public void clear() {
        cartService.clearCart();
    }
}