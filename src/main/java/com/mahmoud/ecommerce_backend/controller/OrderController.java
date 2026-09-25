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
import java.util.Map;

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
    public ApiResponse<?> getUserOrders(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean paged) {

        // Back-compat: no params → legacy full list (batched loads, still flat
        // queries per association class). With `paged=true` (or page+size given)
        // the response becomes the standard pagination envelope.
        if (paged || page != null || size != null) {
            int p = page != null && page >= 0 ? page : 0;
            int s = size != null ? Math.min(Math.max(size, 1), 50) : 10;
            var result = orderService.getUserOrders(org.springframework.data.domain.PageRequest.of(p, s));
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("content", result.getContent());
            body.put("page", result.getNumber());
            body.put("size", result.getSize());
            body.put("totalElements", result.getTotalElements());
            body.put("totalPages", result.getTotalPages());
            return ApiResponse.success(body, "Orders fetched successfully");
        }

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

    @Operation(summary = "Request a return for a delivered order")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/return")
    public ApiResponse<Void> requestReturn(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        orderService.requestReturn(id, body != null ? body.get("reason") : null);
        return ApiResponse.success(null, "Return requested");
    }

    @Operation(summary = "Approve a pending return (delivered → refunded)")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    @PostMapping("/{id}/approve-return")
    public ApiResponse<Void> approveReturn(@PathVariable Long id) {
        orderService.approveReturn(id);
        return ApiResponse.success(null, "Return approved");
    }
}