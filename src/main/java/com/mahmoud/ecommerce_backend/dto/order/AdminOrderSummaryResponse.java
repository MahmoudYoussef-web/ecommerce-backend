package com.mahmoud.ecommerce_backend.dto.order;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderSummaryResponse {
    private Long id;
    private String orderNumber;
    private Instant createdAt;
    private String status;
    private BigDecimal totalAmount;
    private String customerName;
    private String customerEmail;
    private int itemsCount;
    private AddressSnapshot address;
    private String paymentStatus;
}
