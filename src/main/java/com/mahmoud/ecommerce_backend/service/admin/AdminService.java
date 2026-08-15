package com.mahmoud.ecommerce_backend.service.admin;

import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;

import java.util.List;

public interface AdminService {

    List<AdminOrderSummaryResponse> getAllOrders();

    List<UserResponse> getCustomers();
}
