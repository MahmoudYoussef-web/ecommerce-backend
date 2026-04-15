package com.mahmoud.ecommerce_backend.service.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public interface ReportService {

    BigDecimal getTotalRevenue(Instant from, Instant to);

    Map<String, Object> getDashboard(Instant from, Instant to);
}