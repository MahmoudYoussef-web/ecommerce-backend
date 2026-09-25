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

    void markCodPaid(Long orderId);

    /** Dev/test helper: completes the caller's own pending card payment and
     *  marks its order PAID, simulating the Stripe webhook without a real
     *  charge. Only used by the mock-checkout path when Stripe is unconfigured. */
    void mockCompletePayment(Long paymentId);
}