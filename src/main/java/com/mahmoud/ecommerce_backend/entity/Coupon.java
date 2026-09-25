package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.CouponType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Table(
        name = "coupons",
        indexes = {
                @Index(name = "idx_coupon_code", columnList = "code", unique = true),
                @Index(name = "idx_coupon_active", columnList = "active")
        }
)
public class Coupon extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CouponType type = CouponType.PERCENT;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "min_subtotal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minSubtotal = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    public void recordUse() {
        this.usedCount++;
    }
}
