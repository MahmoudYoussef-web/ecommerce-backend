package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.entity.Cart;
import com.mahmoud.ecommerce_backend.entity.CartItem;
import com.mahmoud.ecommerce_backend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "items", source = "cart.cartItems")
    CartResponse toResponse(Cart cart);

    List<CartItemResponse> toItemResponses(List<CartItem> cartItems);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "unitPrice")
    @Mapping(target = "imageUrl", expression = "java(extractImage(cartItem))")
    CartItemResponse toItemResponse(CartItem cartItem);

    default String extractImage(CartItem cartItem) {
        List<ProductImage> images = cartItem.getProduct().getImages();
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