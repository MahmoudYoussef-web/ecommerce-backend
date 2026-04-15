package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.order.*;
import com.mahmoud.ecommerce_backend.service.order.OrderService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;



    @Operation(summary = "Create order")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(
                orderService.createOrder(request),
                "Order created successfully"
        );
    }

    @Operation(summary = "Get current user's orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ApiResponse<List<OrderResponse>> getUserOrders() {
        return ApiResponse.success(
                orderService.getUserOrders(),
                "Orders fetched successfully"
        );
    }

    @Operation(summary = "Get order by id")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN','WAREHOUSE')")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
        return ApiResponse.success(
                orderService.getOrderById(id),
                "Order fetched successfully"
        );
    }



    @Operation(summary = "Mark order as shipped")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @PatchMapping("/{id}/ship")
    public ApiResponse<Void> ship(@PathVariable Long id) {
        orderService.markAsShipped(id);
        return ApiResponse.success(null, "Order marked as shipped");
    }

    @Operation(summary = "Mark order as delivered")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @PatchMapping("/{id}/deliver")
    public ApiResponse<Void> deliver(@PathVariable Long id) {
        orderService.markAsDelivered(id);
        return ApiResponse.success(null, "Order marked as delivered");
    }

    @Operation(summary = "Cancel order")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ApiResponse.success(null, "Order cancelled successfully");
    }
}