package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}