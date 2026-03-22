package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.dto.product.CreateProductRequest;
import com.mahmoud.ecommerce_backend.dto.product.ProductResponse;
import com.mahmoud.ecommerce_backend.dto.product.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    ProductResponse getById(Long id);

    Page<ProductResponse> getAll(Pageable pageable);

    Page<ProductResponse> getByCategory(Long categoryId, Pageable pageable);
}
