package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.service.report.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial & dashboard APIs")
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/revenue")
    public ApiResponse<BigDecimal> revenue(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.success(
                reportService.getTotalRevenue(from, to),
                "Revenue calculated"
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ApiResponse.success(
                reportService.getDashboard(from, to),
                "Dashboard data"
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping(value = "/dashboard/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<String> exportDashboard(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        Map<String, Object> data = reportService.getDashboard(from, to);
        String csv = "from,to,revenue,totalOrders,avgOrderValue\n"
                + from + "," + to + ","
                + data.get("revenue") + ","
                + data.get("totalOrders") + ","
                + data.get("avgOrderValue") + "\n";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"dashboard.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}