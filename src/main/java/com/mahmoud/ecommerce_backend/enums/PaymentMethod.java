package com.mahmoud.ecommerce_backend.enums;

/**
 * Supported payment methods.
 * Add new methods here and update the payment gateway integration accordingly.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    STRIPE,
    BANK_TRANSFER,
    CASH_ON_DELIVERY,
    CRYPTO,
    WALLET
}
