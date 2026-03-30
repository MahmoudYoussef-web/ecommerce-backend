package com.mahmoud.ecommerce_backend.service.payment;

public interface PaymentProvider {

    String createCheckoutSession(Long paymentId);

    void handleWebhook(String payload);
}