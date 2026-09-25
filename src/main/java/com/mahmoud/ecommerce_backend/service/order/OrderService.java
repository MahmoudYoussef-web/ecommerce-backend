package com.mahmoud.ecommerce_backend.service.order;

import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.dto.order.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {


    OrderResponse createOrder(CreateOrderRequest request);


    List<OrderResponse> getUserOrders();

    /**
     * DB-level paginated order history. Items are batch-loaded per page, so
     * query count stays flat regardless of page size or history length.
     */
    Page<OrderResponse> getUserOrders(Pageable pageable);

    OrderResponse getOrderById(Long id);


    void markAsShipped(Long id);

    void markAsDelivered(Long id);

    void cancelOrder(Long id);

    void requestReturn(Long id, String reason);

    void approveReturn(Long id);
}