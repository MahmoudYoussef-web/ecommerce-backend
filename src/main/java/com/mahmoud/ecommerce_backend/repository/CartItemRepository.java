package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    void deleteAllByCartId(Long id);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
}
