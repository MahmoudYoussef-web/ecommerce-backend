package com.mahmoud.ecommerce_backend.dto.order;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private LocalDateTime createdAt;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal totalAmountEgp;
    private BigDecimal exchangeRate;
    private LocalDateTime exchangeRateAt;
    private AddressSnapshot address;
    private List<OrderItemResponse> items;
}