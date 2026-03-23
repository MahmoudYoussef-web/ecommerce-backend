package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.payment.CreatePaymentRequest;
import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create payment for order")
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(
                paymentService.createPayment(request.getOrderId(), request.getMethod()),
                "Payment created successfully"
        );
    }

    @Operation(summary = "Update payment status (ADMIN only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public ApiResponse<PaymentResponse> update(@PathVariable Long id,
                                               @RequestParam PaymentStatus status) {
        return ApiResponse.success(
                paymentService.updateStatus(id, status),
                "Payment status updated successfully"
        );
    }
}