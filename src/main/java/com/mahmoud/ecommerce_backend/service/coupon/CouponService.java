package com.mahmoud.ecommerce_backend.service.coupon;

import com.mahmoud.ecommerce_backend.dto.coupon.CouponResponse;
import com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    CouponResponse create(CreateCouponRequest request);

    CouponResponse update(Long id, CreateCouponRequest request);

    void delete(Long id);

    List<CouponResponse> list();

    /**
     * Validates the coupon against the given subtotal and returns the
     * discount amount (never negative, never above subtotal).
     */
    BigDecimal previewDiscount(String code, BigDecimal subtotal);

    /**
     * Validates + records one use. Must be called inside the order
     * transaction so a failed order never consumes the coupon.
     */
    BigDecimal applyCoupon(String code, BigDecimal subtotal);
}
