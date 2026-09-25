package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.coupon.CouponResponse;
import com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest;
import com.mahmoud.ecommerce_backend.service.coupon.CouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Discount coupon APIs")
public class CouponController {

    private final CouponService couponService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        return ApiResponse.success(couponService.create(request), "Coupon created");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<CouponResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateCouponRequest request) {
        return ApiResponse.success(couponService.update(id, request), "Coupon updated");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ApiResponse.success(null, "Coupon deleted");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<List<CouponResponse>> list() {
        return ApiResponse.success(couponService.list(), "Coupons fetched successfully");
    }

    /**
     * Authenticated preview: how much would this code discount the given
     * subtotal? Never consumes the coupon (use order creation to apply).
     */
    @GetMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(
            @RequestParam String code,
            @RequestParam BigDecimal subtotal) {
        BigDecimal discount = couponService.previewDiscount(code, subtotal);
        return ApiResponse.success(
                Map.of("code", code.trim().toUpperCase(), "discount", discount),
                "Coupon is valid");
    }
}
