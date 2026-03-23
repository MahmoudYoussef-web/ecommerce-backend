package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.category.*;
import com.mahmoud.ecommerce_backend.service.category.CategoryService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category management APIs")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Get all categories")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.success(
                categoryService.getAll(),
                "Categories fetched successfully"
        );
    }

    @Operation(summary = "Get category by slug")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{slug}")
    public ApiResponse<CategoryResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(
                categoryService.getBySlug(slug),
                "Category fetched successfully"
        );
    }

    @Operation(summary = "Create category")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success(
                categoryService.create(request),
                "Category created successfully"
        );
    }

    @Operation(summary = "Update category")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success(
                categoryService.update(id, request),
                "Category updated successfully"
        );
    }

    @Operation(summary = "Delete category")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success(null, "Category deleted successfully");
    }
}