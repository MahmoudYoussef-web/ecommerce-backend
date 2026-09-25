package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.order.AdminOrderSummaryResponse;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.service.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
@Tag(name = "Admin", description = "Admin management APIs")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;

    @Operation(summary = "Get orders page with optional status/search filters")
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {

        OrderStatus statusFilter = parseStatus(status);

        Page<AdminOrderSummaryResponse> result = adminService.getOrders(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")),
                statusFilter,
                q
        );

        Map<String, Object> body = Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );

        return ApiResponse.success(body, "Orders fetched successfully");
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid order status: " + status);
        }
    }

    @Operation(summary = "Export filtered orders as CSV (capped at 5000 rows)")
    @GetMapping(value = "/orders/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<String> exportOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {

        var rows = adminService.exportOrders(parseStatus(status), q);
        StringBuilder csv = new StringBuilder("id,orderNumber,createdAt,status,totalAmount,customerName,customerEmail,itemsCount,paymentStatus\n");
        for (var o : rows) {
            csv.append(csv(o.getId())).append(',')
                    .append(csv(o.getOrderNumber())).append(',')
                    .append(csv(o.getCreatedAt())).append(',')
                    .append(csv(o.getStatus())).append(',')
                    .append(csv(o.getTotalAmount())).append(',')
                    .append(csv(o.getCustomerName())).append(',')
                    .append(csv(o.getCustomerEmail())).append(',')
                    .append(csv(o.getItemsCount())).append(',')
                    .append(csv(o.getPaymentStatus())).append('\n');
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"orders.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).replace("\"", "\"\"");
        return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\"" + s + "\"" : s;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all customers")
    @GetMapping("/customers")
    public ApiResponse<?> getCustomers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean paged) {

        // Back-compat array when no paging params; standard envelope otherwise.
        if (paged || page != null || size != null) {
            int p = page != null && page >= 0 ? page : 0;
            int s = size != null ? Math.min(Math.max(size, 1), 100) : 20;
            var result = adminService.getCustomers(org.springframework.data.domain.PageRequest.of(p, s));
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("content", result.getContent());
            body.put("page", result.getNumber());
            body.put("size", result.getSize());
            body.put("totalElements", result.getTotalElements());
            body.put("totalPages", result.getTotalPages());
            return ApiResponse.success(body, "Customers fetched successfully");
        }

        return ApiResponse.success(
                adminService.getCustomers(),
                "Customers fetched successfully"
        );
    }
}
