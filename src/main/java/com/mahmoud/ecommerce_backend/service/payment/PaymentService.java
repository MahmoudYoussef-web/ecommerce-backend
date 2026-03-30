package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;

import java.math.BigDecimal;

public interface PaymentService {

    PaymentResponse createPayment(Long orderId, PaymentMethod method);

    PaymentResponse updateStatus(Long paymentId, PaymentStatus status);

    void processWebhook(String eventId, Long paymentId, PaymentStatus status, String reference);

    void processStripeWebhook(String eventId,
                              Long paymentId,
                              PaymentStatus status,
                              String reference,
                              BigDecimal amount,
                              String currency);

    String createCheckoutSession(Long paymentId);
}