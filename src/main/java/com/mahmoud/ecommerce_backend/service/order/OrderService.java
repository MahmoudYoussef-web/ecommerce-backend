package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;

import java.util.List;

public interface OrderService {


    OrderResponse createOrder(CreateOrderRequest request);


    List<OrderResponse> getUserOrders();

    OrderResponse getOrderById(Long id);


    void markAsShipped(Long id);

    void markAsDelivered(Long id);

    void cancelOrder(Long id);
}