package com.mahmoud.ecommerce_backend.service.coupon;

import com.mahmoud.ecommerce_backend.dto.coupon.CouponResponse;
import com.mahmoud.ecommerce_backend.dto.coupon.CreateCouponRequest;
import com.mahmoud.ecommerce_backend.entity.Coupon;
import com.mahmoud.ecommerce_backend.enums.CouponType;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        String code = normalized(request.getCode());
        couponRepository.findByCodeIgnoreCase(code).ifPresent(c -> {
            throw new BadRequestException("Coupon code already exists");
        });
        Coupon coupon = Coupon.builder()
                .code(code)
                .type(request.getType())
                .value(request.getValue())
                .minSubtotal(nvl(request.getMinSubtotal()))
                .maxDiscount(request.getMaxDiscount())
                .active(request.getActive() == null || request.getActive())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .usageLimit(request.getUsageLimit())
                .build();
        validateShape(coupon);
        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CreateCouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = normalized(request.getCode());
            couponRepository.findByCodeIgnoreCase(code).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BadRequestException("Coupon code already exists");
                }
            });
            coupon.setCode(code);
        }
        if (request.getType() != null) coupon.setType(request.getType());
        if (request.getValue() != null) coupon.setValue(request.getValue());
        if (request.getMinSubtotal() != null) coupon.setMinSubtotal(request.getMinSubtotal());
        if (request.getMaxDiscount() != null) coupon.setMaxDiscount(request.getMaxDiscount());
        if (request.getActive() != null) coupon.setActive(request.getActive());
        if (request.getStartsAt() != null) coupon.setStartsAt(request.getStartsAt());
        if (request.getEndsAt() != null) coupon.setEndsAt(request.getEndsAt());
        if (request.getUsageLimit() != null) coupon.setUsageLimit(request.getUsageLimit());
        validateShape(coupon);
        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        coupon.setDeleted(true);
        couponRepository.save(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> list() {
        return couponRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal previewDiscount(String code, BigDecimal subtotal) {
        return discountFor(resolve(code), nvl(subtotal));
    }

    @Override
    @Transactional
    public BigDecimal applyCoupon(String code, BigDecimal subtotal) {
        Coupon coupon = resolve(code);
        BigDecimal discount = discountFor(coupon, nvl(subtotal));
        coupon.recordUse();
        couponRepository.save(coupon);
        return discount;
    }

    // ================= INTERNAL =================

    private Coupon resolve(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Coupon code is required");
        }
        Coupon coupon = couponRepository.findByCodeIgnoreCase(normalized(code))
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        if (!coupon.isActive()) {
            throw new BadRequestException("Coupon is not active");
        }
        Instant now = Instant.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new BadRequestException("Coupon is not active yet");
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new BadRequestException("Coupon has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("Coupon usage limit reached");
        }
        return coupon;
    }

    private BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Coupon requires a non-empty subtotal");
        }
        if (coupon.getMinSubtotal() != null && subtotal.compareTo(coupon.getMinSubtotal()) < 0) {
            throw new BadRequestException("Order subtotal is below the coupon minimum");
        }
        BigDecimal discount;
        if (coupon.getType() == CouponType.PERCENT) {
            if (coupon.getValue().compareTo(BigDecimal.ZERO) <= 0
                    || coupon.getValue().compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("Invalid percent value");
            }
            discount = subtotal.multiply(coupon.getValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getValue();
        }
        if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
            discount = coupon.getMaxDiscount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateShape(Coupon coupon) {
        if (coupon.getValue() == null || coupon.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Coupon value must be positive");
        }
        if (coupon.getType() == CouponType.PERCENT
                && coupon.getValue().compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Percent coupon cannot exceed 100");
        }
        if (coupon.getStartsAt() != null && coupon.getEndsAt() != null
                && coupon.getEndsAt().isBefore(coupon.getStartsAt())) {
            throw new BadRequestException("Coupon end date is before start date");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsageLimit() <= 0) {
            throw new BadRequestException("Usage limit must be positive");
        }
    }

    private String normalized(String code) {
        return code.trim().toUpperCase();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private CouponResponse toResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .type(coupon.getType() != null ? coupon.getType().name() : null)
                .value(coupon.getValue())
                .minSubtotal(coupon.getMinSubtotal())
                .maxDiscount(coupon.getMaxDiscount())
                .active(coupon.isActive())
                .startsAt(coupon.getStartsAt())
                .endsAt(coupon.getEndsAt())
                .usageLimit(coupon.getUsageLimit())
                .usedCount(coupon.getUsedCount())
                .build();
    }
}
