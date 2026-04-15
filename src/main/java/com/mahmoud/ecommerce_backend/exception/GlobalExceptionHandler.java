package com.mahmoud.ecommerce_backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {
        log.warn("API Exception [{}]: {} path={}", ex.getErrorCode(), ex.getMessage(), request.getRequestURI());

        return build(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode(), request, null);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        log.warn("Validation failed path={} errors={}", request.getRequestURI(), errors);

        return build(400, "Validation failed", "VALIDATION_ERROR", request, errors);
    }


    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {

        Map<String, String> errors = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            );
        }

        log.warn("Constraint violation path={} errors={}", request.getRequestURI(), errors);

        return build(400, "Validation failed", "VALIDATION_ERROR", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {

        return build(
                400,
                "Invalid value for parameter: " + ex.getName(),
                "INVALID_PARAMETER",
                request,
                null
        );
    }


    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {

        return build(
                409,
                "Resource was modified concurrently. Please retry.",
                "CONCURRENT_MODIFICATION",
                request,
                null
        );
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {

        String message = "Database constraint violation";

        if (ex.getMostSpecificCause() != null) {
            message = ex.getMostSpecificCause().getMessage();
        }

        log.error("DB error path={} msg={}", request.getRequestURI(), message);

        return build(
                409,
                message,
                "DATA_INTEGRITY_ERROR",
                request,
                null
        );
    }


    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {

        return build(403, "Access denied", "ACCESS_DENIED", request, null);
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {

        return build(400, ex.getMessage(), "INVALID_ARGUMENT", request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {

        return build(400, ex.getMessage(), "INVALID_STATE", request, null);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error("Unexpected error path={}", request.getRequestURI(), ex);

        return build(500, "Internal server error", "INTERNAL_ERROR", request, null);
    }


    private ResponseEntity<ApiErrorResponse> build(
            int status,
            String message,
            String code,
            HttpServletRequest request,
            Map<String, String> errors
    ) {

        ApiErrorResponse response = (errors == null)
                ? new ApiErrorResponse(status, message, code, request.getRequestURI())
                : new ApiErrorResponse(status, message, code, request.getRequestURI(), errors);

        return ResponseEntity.status(status).body(response);
    }
}