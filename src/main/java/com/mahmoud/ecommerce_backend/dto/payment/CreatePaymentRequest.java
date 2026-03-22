package com.mahmoud.ecommerce_backend.dto.payment;

import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private Long orderId;

    @NotNull
    private PaymentMethod method;
}