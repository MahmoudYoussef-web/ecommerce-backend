package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;

public interface PaymentService {

    PaymentResponse createPayment(Long orderId, PaymentMethod method);

    PaymentResponse updateStatus(Long paymentId, PaymentStatus status);
}