package com.mahmoud.ecommerce_backend.dto.coupon;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponResponse {
    private Long id;
    private String code;
    private String type;
    private BigDecimal value;
    private BigDecimal minSubtotal;
    private BigDecimal maxDiscount;
    private boolean active;
    private Instant startsAt;
    private Instant endsAt;
    private Integer usageLimit;
    private int usedCount;
}
