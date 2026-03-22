// ===================== OrderController =====================
package com.mahmoud.ecommerce_backend.controller;

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
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    public List<OrderResponse> getUserOrders() {
        return orderService.getUserOrders();
    }
}