package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.order.*;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.OrderItem;
import com.mahmoud.ecommerce_backend.entity.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "address", source = "shippingAddress")
    @Mapping(target = "status", expression = "java(order.getStatus() != null ? order.getStatus().name() : null)")
    OrderResponse toResponse(Order order);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "variantId", source = "variantId")
    @Mapping(target = "productName", source = "productName")
    @Mapping(target = "productSku", source = "productSku")
    @Mapping(target = "productImageUrl", source = "productImageUrl")
    @Mapping(target = "priceAtPurchase", source = "priceAtPurchase")
    OrderItemResponse toItemResponse(OrderItem item);

    @Mapping(target = "street", source = "addressLine1")
    @Mapping(target = "zipCode", source = "postalCode")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "country", source = "country")
    com.mahmoud.ecommerce_backend.dto.order.AddressSnapshot map(
            com.mahmoud.ecommerce_backend.entity.AddressSnapshot snapshot
    );

    AddressSnapshot toSnapshot(Address address);

    default LocalDateTime map(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}