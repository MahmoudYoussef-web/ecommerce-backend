package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.product.*;
import com.mahmoud.ecommerce_backend.service.product.ProductService;
import com.mahmoud.ecommerce_backend.service.product.ProductSorts;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    /** Server-side clamp: oversized page requests can never amplify cache-miss cost or payload size. */
    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int requested) {
        int safe = requested <= 0 ? 10 : requested;
        return Math.min(safe, MAX_PAGE_SIZE);
    }



    @Operation(summary = "Create product")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success(
                productService.createProduct(request),
                "Product created successfully"
        );
    }

    @Operation(summary = "Update product")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(
                productService.updateProduct(id, request),
                "Product updated successfully"
        );
    }

    @Operation(summary = "Delete product")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.success(null, "Product deleted successfully");
    }


    @Operation(summary = "Get product by id")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(
                productService.getById(id),
                "Product fetched successfully"
        );
    }

    @Operation(summary = "Get all products with pagination")
    @GetMapping
    public ApiResponse<Map<String, Object>> getAll(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String sort) {

        Page<ProductResponse> result =
                productService.getAll(PageRequest.of(page, clampSize(size), ProductSorts.resolve(sort)));

        return ApiResponse.success(buildPageResponse(result), "Products fetched successfully");
    }

    @Operation(summary = "Bounded per-category sections for the homepage")
    @GetMapping("/home-sections")
    public ApiResponse<HomeSectionsResponse> homeSections(
            @RequestParam(defaultValue = "6") int perCategory) {

        return ApiResponse.success(
                productService.getHomeSections(clampSize(Math.max(perCategory, 1))),
                "Home sections fetched successfully");
    }

    @Operation(summary = "Get products by category")
    @GetMapping("/category/{categoryId}")
    public ApiResponse<Map<String, Object>> getByCategory(@PathVariable Long categoryId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String sort) {

        Page<ProductResponse> result =
                productService.getByCategory(categoryId, PageRequest.of(page, clampSize(size), ProductSorts.resolve(sort)));

        return ApiResponse.success(buildPageResponse(result), "Category products fetched successfully");
    }

    @Operation(summary = "Search products with filters")
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Page<ProductResponse> result = productService.searchProducts(
                name,
                minPrice,
                maxPrice,
                categoryId,
                inStock,
                PageRequest.of(page, clampSize(size), ProductSorts.resolve(sort))
        );

        return ApiResponse.success(buildPageResponse(result), "Search results fetched successfully");
    }



    private Map<String, Object> buildPageResponse(Page<?> page) {
        return Map.of(
                "content", page.getContent(),
                "page", page.getNumber(),
                "size", page.getSize(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages()
        );
    }
}