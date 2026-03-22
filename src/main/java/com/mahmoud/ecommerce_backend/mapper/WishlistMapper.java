package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.wishlist.*;
import com.mahmoud.ecommerce_backend.entity.WishlistItem;
import com.mahmoud.ecommerce_backend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface WishlistMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", expression = "java(extractImage(item))")
    WishlistItemResponse toItemResponse(WishlistItem item);

    default String extractImage(WishlistItem item) {
        List<ProductImage> images = item.getProduct().getImages();
        if (images == null || images.isEmpty()) return null;

        return images.stream()
                .sorted(Comparator
                        .comparing(ProductImage::isPrimaryImage).reversed()
                        .thenComparing(ProductImage::getDisplayOrder))
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(null);
    }
}