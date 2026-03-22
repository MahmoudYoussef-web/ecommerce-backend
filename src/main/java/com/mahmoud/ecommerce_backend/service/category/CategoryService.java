package com.mahmoud.ecommerce_backend.service.category;

import com.mahmoud.ecommerce_backend.dto.category.*;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAll();

    CategoryResponse getBySlug(String slug);

    CategoryResponse create(CreateCategoryRequest request);

    CategoryResponse update(Long id, UpdateCategoryRequest request);

    void delete(Long id);
}