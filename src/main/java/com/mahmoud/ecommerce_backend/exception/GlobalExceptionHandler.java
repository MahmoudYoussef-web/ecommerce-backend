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
import org.springframework.transaction.TransactionSystemException;
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

    /**
     * Bean-validation failures raised at flush time (e.g. entity @Digits on a
     * money column) surface wrapped in a TransactionSystemException. Map the
     * unwrapped violations to a client-facing 400 instead of a 500.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiErrorResponse> handleTransactionSystem(
            TransactionSystemException ex,
            HttpServletRequest request
    ) {

        Throwable root = ex;

        while (root.getCause() != null && root != root.getCause()) {
            root = root.getCause();
            if (root instanceof ConstraintViolationException violationEx) {
                Map<String, String> errors = new HashMap<>();
                for (ConstraintViolation<?> violation : violationEx.getConstraintViolations()) {
                    errors.putIfAbsent(
                            violation.getPropertyPath().toString(),
                            violation.getMessage()
                    );
                }

                log.warn("Entity validation failed path={} errors={}", request.getRequestURI(), errors);

                return build(400, "Validation failed", "VALIDATION_ERROR", request, errors);
            }
        }

        log.error("Transaction system error path={}", request.getRequestURI(), ex);

        return build(500, "Internal server error", "INTERNAL_ERROR", request, null);
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

        // Server-side log keeps the driver detail for debugging; the client
        // receives a fixed generic message — constraint names, SQL fragments
        // and schema detail must never leave the process.
        log.error("DB integrity violation path={} detail={}",
                request.getRequestURI(),
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());

        return build(
                409,
                "The request conflicts with existing data",
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


    /**
     * Spring Security authentication failures surfaced through controllers
     * (e.g. wrong password / disabled / locked accounts during login).
     * Previously these fell into the generic handler and produced a 500;
     * they must be a stable 401 with a non-committal message.
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex,
            HttpServletRequest request
    ) {

        log.warn("Bad credentials path={}", request.getRequestURI());

        return build(401, "Invalid email or password", "INVALID_CREDENTIALS", request, null);
    }

    @ExceptionHandler({
            org.springframework.security.authentication.DisabledException.class,
            org.springframework.security.authentication.LockedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleAccountStatus(
            org.springframework.security.core.AuthenticationException ex,
            HttpServletRequest request
    ) {

        log.warn("Account status rejected authentication path={} type={}",
                request.getRequestURI(), ex.getClass().getSimpleName());

        return build(401, "Authentication failed", "UNAUTHORIZED", request, null);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            org.springframework.security.core.AuthenticationException ex,
            HttpServletRequest request
    ) {

        log.warn("Authentication failure path={} type={}",
                request.getRequestURI(), ex.getClass().getSimpleName());

        return build(401, "Authentication failed", "UNAUTHORIZED", request, null);
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {

        // Exception text may carry internal details (state names, class
        // internals); log server-side, return a generic client payload.
        log.warn("Invalid argument path={} detail={}", request.getRequestURI(), ex.getMessage());

        return build(400, "Invalid request", "INVALID_ARGUMENT", request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request
    ) {

        log.warn("Illegal state path={} detail={}", request.getRequestURI(), ex.getMessage());

        return build(409, "Request cannot be processed in the current state", "INVALID_STATE", request, null);
    }


    /**
     * No handler for the requested path (e.g. Swagger UI in production where
     * springdoc is disabled, or any unknown URL). Returns a clean generic 404
     * instead of falling through to the 500 handler.
     */
    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(
            org.springframework.web.servlet.NoHandlerFoundException ex,
            HttpServletRequest request
    ) {

        log.info("No handler path={} method={}", request.getRequestURI(), request.getMethod());

        return build(404, "Resource not found", "NOT_FOUND", request, null);
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