package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.wishlist.*;
import com.mahmoud.ecommerce_backend.entity.Product;
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
    @Mapping(target = "price", source = "item.product.price")
    @Mapping(target = "discountedPrice", source = "item.product.discountedPrice")
    @Mapping(target = "stockQuantity", source = "item.product.stockQuantity")
    @Mapping(target = "imageUrls", expression = "java(extractImageUrls(item))")
    @Mapping(target = "averageRating", source = "item.product.averageRating")
    @Mapping(target = "reviewCount", source = "item.product.reviewCount")
    WishlistItemResponse toItemResponse(WishlistItem item);

    default String extractImage(WishlistItem item) {
        List<String> urls = extractImageUrls(item);
        return urls.isEmpty() ? null : urls.get(0);
    }

    default List<String> extractImageUrls(WishlistItem item) {
        Product product = item.getProduct();
        if (product == null) return List.of();

        List<ProductImage> images = product.getImages();
        if (images == null || images.isEmpty()) return List.of();
        return images.stream()
                .sorted(Comparator
                        .comparing(ProductImage::isPrimaryImage).reversed()
                        .thenComparing(ProductImage::getDisplayOrder))
                .map(ProductImage::getUrl)
                .toList();
    }
}
