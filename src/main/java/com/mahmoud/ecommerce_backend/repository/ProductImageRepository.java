package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;



public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}