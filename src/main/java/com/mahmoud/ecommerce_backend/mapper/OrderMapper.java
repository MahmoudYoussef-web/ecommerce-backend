package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.order.*;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.OrderItem;
import com.mahmoud.ecommerce_backend.entity.Address;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
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
    OrderResponse toResponse(Order order);

    List<OrderItemResponse> toItemResponses(List<OrderItem> items);

    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "productName", source = "productName")
    @Mapping(target = "productSku", source = "productSku")
    @Mapping(target = "productImageUrl", source = "productImageUrl")
    @Mapping(target = "priceAtPurchase", source = "priceAtPurchase")
    OrderItemResponse toItemResponse(OrderItem item);

    AddressSnapshot toSnapshot(Address address);

    default LocalDateTime map(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}