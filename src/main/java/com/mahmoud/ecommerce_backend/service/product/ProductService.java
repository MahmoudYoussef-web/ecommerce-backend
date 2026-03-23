package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.dto.product.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    ProductResponse getById(Long id);

    Page<ProductResponse> getAll(Pageable pageable);

    Page<ProductResponse> getByCategory(Long categoryId, Pageable pageable);

    void deleteProduct(Long id);


    Page<ProductResponse> searchProducts(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long categoryId,
            Boolean inStock,
            Pageable pageable
    );
}