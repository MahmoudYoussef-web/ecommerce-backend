package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByCategoryName(String category);

    List<Product> findByBrand(String brand);

    List<Product> findByCategoryNameAndBrand(String category, String brand);

    List<Product> findByName(String name);

    List<Product> findByBrandAndName(String brand, String name);

    List<Product> findByCategoryId(Long categoryId);

    Optional<Product> findBySlug(String slug);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Long countByBrandAndName(String brand, String name);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
}