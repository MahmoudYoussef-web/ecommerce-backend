package com.mahmoud.ecommerce_backend.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.exception.ApiErrorResponse;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String message = resolveMessage(ex);


        ApiErrorResponse error = new ApiErrorResponse(
                401,
                message,
                "UNAUTHORIZED",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private String resolveMessage(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) return "Invalid email or password";
        if (ex instanceof DisabledException) return "Account disabled or not verified";
        if (ex instanceof LockedException) return "Account locked";
        if (ex instanceof InsufficientAuthenticationException) return "Authentication required";
        return "Unauthorized";
    }
}