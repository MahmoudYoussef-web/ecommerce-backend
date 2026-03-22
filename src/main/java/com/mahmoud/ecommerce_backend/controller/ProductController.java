package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.product.*;
import com.mahmoud.ecommerce_backend.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping
    public Page<ProductResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return productService.getAll(PageRequest.of(page, size));
    }

    @GetMapping("/category/{categoryId}")
    public Page<ProductResponse> getByCategory(@PathVariable Long categoryId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return productService.getByCategory(categoryId, PageRequest.of(page, size));
    }
}