package com.mahmoud.ecommerce_backend.service.payment;

public interface WebhookValidator {

    void validate(String payload, String signature, String timestampHeader);
}