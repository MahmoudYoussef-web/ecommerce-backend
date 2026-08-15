package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
@Tag(name = "Meta", description = "Public app metadata endpoints")
public class MetaController {

    @Value("${app.currency.egp-per-usd}")
    private BigDecimal egpPerUsd;

    @Operation(summary = "Get the current EGP display rate")
    @GetMapping("/currency")
    public ApiResponse<Map<String, Object>> currency() {
        return ApiResponse.success(Map.of(
                "egpPerUsd", egpPerUsd
        ));
    }
}
