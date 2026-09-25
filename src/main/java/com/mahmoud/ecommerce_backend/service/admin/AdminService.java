package com.mahmoud.ecommerce_backend.service.admin;

import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {

    Page<AdminOrderSummaryResponse> getOrders(Pageable pageable, OrderStatus status, String search);

    /**
     * Unpaged export of the filtered orders (capped) for CSV download.
     */
    List<AdminOrderSummaryResponse> exportOrders(OrderStatus status, String search);

    List<UserResponse> getCustomers();

    Page<UserResponse> getCustomers(Pageable pageable);
}
