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