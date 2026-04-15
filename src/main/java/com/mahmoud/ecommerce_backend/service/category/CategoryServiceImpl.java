package com.mahmoud.ecommerce_backend.service.category;

import com.mahmoud.ecommerce_backend.dto.category.*;
import com.mahmoud.ecommerce_backend.entity.Category;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ConflictException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.CategoryMapper;
import com.mahmoud.ecommerce_backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;


    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getBySlug(String slug) {

        if (slug == null || slug.isBlank()) {
            throw new BadRequestException("Slug must not be empty");
        }

        Category category = categoryRepository.findBySlug(slug.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return categoryMapper.toResponse(category);
    }


    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {

        validateCreate(request);

        String normalizedSlug = normalizeSlug(request.getSlug());

        if (categoryRepository.existsBySlug(normalizedSlug)) {
            throw new ConflictException("Category with same slug already exists");
        }

        Category category = categoryMapper.toEntity(request);
        category.setSlug(normalizedSlug);

        categoryRepository.save(category);

        return categoryMapper.toResponse(category);
    }


    @Override
    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        Category category = getOrThrow(id);

        if (request.getName() != null) {
            category.setName(request.getName());
        }

        if (request.getSlug() != null) {

            String normalizedSlug = normalizeSlug(request.getSlug());

            if (!normalizedSlug.equals(category.getSlug()) &&
                    categoryRepository.existsBySlug(normalizedSlug)) {
                throw new ConflictException("Category with same slug already exists");
            }

            category.setSlug(normalizedSlug);
        }

        return categoryMapper.toResponse(category);
    }


    @Override
    @Transactional
    public void delete(Long id) {

        Category category = getOrThrow(id);


        category.setDeleted(true);
    }



    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void validateCreate(CreateCategoryRequest request) {

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Category name is required");
        }

        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new BadRequestException("Slug is required");
        }
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }
}