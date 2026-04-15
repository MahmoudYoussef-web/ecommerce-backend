package com.mahmoud.ecommerce_backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String message;
    private final String errorCode;
    private final String path;
    private final Instant timestamp;
    private final Map<String, String> errors;

    public ApiErrorResponse(
            int status,
            String message,
            String errorCode,
            String path
    ) {
        this(status, message, errorCode, path, null);
    }

    public ApiErrorResponse(
            int status,
            String message,
            String errorCode,
            String path,
            Map<String, String> errors
    ) {
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
        this.path = path;
        this.timestamp = Instant.now();
        this.errors = (errors == null || errors.isEmpty()) ? null : errors;
    }
}