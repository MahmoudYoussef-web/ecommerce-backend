package com.mahmoud.ecommerce_backend.dto.order;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private Long addressId;

    private String customerNotes;
}