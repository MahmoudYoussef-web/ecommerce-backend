package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.service.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
@Tag(name = "Admin", description = "Admin management APIs")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Get all orders")
    @GetMapping("/orders")
    public ApiResponse<List<AdminOrderSummaryResponse>> getAllOrders() {
        return ApiResponse.success(
                adminService.getAllOrders(),
                "Orders fetched successfully"
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all customers")
    @GetMapping("/customers")
    public ApiResponse<List<UserResponse>> getCustomers() {
        return ApiResponse.success(
                adminService.getCustomers(),
                "Customers fetched successfully"
        );
    }
}
