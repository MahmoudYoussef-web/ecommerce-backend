package com.mahmoud.ecommerce_backend.service.cart;

import com.mahmoud.ecommerce_backend.dto.cart.AddToCartRequest;
import com.mahmoud.ecommerce_backend.dto.cart.CartResponse;
import com.mahmoud.ecommerce_backend.dto.cart.UpdateCartItemRequest;

public interface CartService {

    CartResponse getCart();

    CartResponse addItem(AddToCartRequest request);

    CartResponse updateItem(UpdateCartItemRequest request);


    CartResponse removeItem(Long productId, Long variantId);

    void clearCart();
}