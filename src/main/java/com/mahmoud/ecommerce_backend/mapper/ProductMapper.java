package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.product.*;
import com.mahmoud.ecommerce_backend.dto.category.CategoryResponse;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category", source = "category")
    @Mapping(target = "imageUrls", expression = "java(mapImages(product.getImages()))")
    ProductResponse toResponse(Product product);

    /**
     * Explicit assembly for LIST pages: images and category are supplied by
     * the caller from batched queries, so lazy collections on the entity are
     * never touched (no per-row queries on cache misses).
     */
    default ProductResponse toResponse(Product product, CategoryResponse categoryResponse, java.util.List<String> sortedUrls) {
        if (product == null) return null;

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setDiscountedPrice(product.getDiscountedPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setAverageRating(product.getAverageRating() == null
                ? null
                : product.getAverageRating().doubleValue());
        response.setReviewCount(product.getReviewCount());
        response.setCategory(categoryResponse);
        response.setImageUrls(sortedUrls == null ? java.util.List.of() : sortedUrls);
        return response;
    }

    default CategoryResponse toCategoryResponse(com.mahmoud.ecommerce_backend.entity.Category category) {
        if (category == null) return null;
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setSlug(category.getSlug());
        response.setDescription(category.getDescription());
        response.setImageUrl(category.getImageUrl());
        return response;
    }

    Product toEntity(CreateProductRequest request);

    Product toEntity(UpdateProductRequest request);

    default List<String> mapImages(List<ProductImage> images) {
        if (images == null) return List.of();
        return images.stream()
                .sorted(Comparator
                        .comparing(ProductImage::isPrimaryImage).reversed()
                        .thenComparing(ProductImage::getDisplayOrder))
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());
    }
}