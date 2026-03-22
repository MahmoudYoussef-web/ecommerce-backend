package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.payment.CreatePaymentRequest;
import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.service.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(
                request.getOrderId(),
                request.getMethod()
        );
    }

    @PutMapping("/{id}/status")
    public PaymentResponse update(@PathVariable Long id,
                                  @RequestParam PaymentStatus status) {
        return paymentService.updateStatus(id, status);
    }
}