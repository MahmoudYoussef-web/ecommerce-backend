package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}