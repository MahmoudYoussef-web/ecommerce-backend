package com.mahmoud.ecommerce_backend.dto.coupon;

import com.mahmoud.ecommerce_backend.enums.CouponType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCouponRequest {

    @NotBlank(message = "code is required")
    @Size(max = 50)
    private String code;

    @NotNull(message = "type is required")
    private CouponType type;

    @NotNull(message = "value is required")
    @DecimalMin(value = "0.01", message = "value must be positive")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal value;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal minSubtotal;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal maxDiscount;

    private Boolean active;
    private Instant startsAt;
    private Instant endsAt;
    private Integer usageLimit;
}
