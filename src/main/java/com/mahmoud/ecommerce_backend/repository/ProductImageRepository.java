package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Batched image fetch for listing pages — one query per page instead of one per product. */
    List<ProductImage> findByProductIdIn(Collection<Long> productIds);
}
