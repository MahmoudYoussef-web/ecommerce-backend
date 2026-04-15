package com.mahmoud.ecommerce_backend.service.report;

import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository paymentRepository;

    @Override
    public BigDecimal getTotalRevenue(Instant from, Instant to) {

        List<Payment> payments = paymentRepository
                .findByStatusAndPaidAtBetween(
                        PaymentStatus.COMPLETED,
                        from,
                        to
                );

        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Map<String, Object> getDashboard(Instant from, Instant to) {

        List<Payment> payments = paymentRepository
                .findByStatusAndPaidAtBetween(
                        PaymentStatus.COMPLETED,
                        from,
                        to
                );

        BigDecimal revenue = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = payments.size();

        BigDecimal avgOrderValue = totalOrders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP);

        return Map.of(
                "revenue", revenue,
                "totalOrders", totalOrders,
                "avgOrderValue", avgOrderValue
        );
    }
}