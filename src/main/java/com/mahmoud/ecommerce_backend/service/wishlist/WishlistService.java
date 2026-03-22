package com.mahmoud.ecommerce_backend.service.wishlist;

import com.mahmoud.ecommerce_backend.dto.wishlist.WishlistResponse;

public interface WishlistService {

    WishlistResponse getWishlist();

    WishlistResponse addProduct(Long productId);

    WishlistResponse removeProduct(Long productId);
}