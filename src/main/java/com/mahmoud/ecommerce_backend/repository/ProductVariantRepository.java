package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
}