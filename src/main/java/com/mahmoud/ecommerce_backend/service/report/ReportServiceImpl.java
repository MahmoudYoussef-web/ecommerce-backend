package com.mahmoud.ecommerce_backend.service.report;

import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(Instant from, Instant to) {

        Object revenue = revenueAggregate(from, to).get("revenue");
        return revenue instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
    }

    /**
     * Single aggregate query (SUM/COUNT) instead of hydrating every completed
     * payment in the range. Numerically equivalent to the previous Java-side
     * reduction: amounts share scale(2) so SUM preserves it; average keeps the
     * same HALF_UP rounding at scale 2.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(Instant from, Instant to) {

        List<Object[]> rows = paymentRepository.aggregateRevenue(
                PaymentStatus.COMPLETED, from, to);

        Object[] row = rows.isEmpty() ? new Object[]{null, 0L} : rows.get(0);

        BigDecimal sum = row[0] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
        long count = ((Number) row[1]).longValue();

        BigDecimal avgOrderValue = count == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        // Preserve the exact response shape the frontend already consumes.
        return Map.of(
                "revenue", sum,
                "totalOrders", count,
                "avgOrderValue", avgOrderValue
        );
    }

    private Map<String, Object> revenueAggregate(Instant from, Instant to) {
        return getDashboard(from, to);
    }
}
