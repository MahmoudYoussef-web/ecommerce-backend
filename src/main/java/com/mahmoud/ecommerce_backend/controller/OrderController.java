package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.order.*;
import com.mahmoud.ecommerce_backend.service.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(
                orderService.createOrder(request),
                "Order created successfully"
        );
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getUserOrders() {
        return ApiResponse.success(
                orderService.getUserOrders(),
                "Orders fetched successfully"
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable Long id) {
        return ApiResponse.success(
                orderService.getOrderById(id),
                "Order fetched successfully"
        );
    }
}