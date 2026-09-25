package com.mahmoud.ecommerce_backend.dto.order;

import com.mahmoud.ecommerce_backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "addressId is required")
    private Long addressId;

    @Size(max = 1000, message = "customerNotes must not exceed 1000 characters")
    private String customerNotes;

    private PaymentMethod paymentMethod;

    @Size(max = 50, message = "couponCode must not exceed 50 characters")
    private String couponCode;
}