package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
}
