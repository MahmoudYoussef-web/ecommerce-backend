package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);
}