package com.mahmoud.ecommerce_backend.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.exception.ApiErrorResponse;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiErrorResponse error = new ApiErrorResponse(
                401,
                "Unauthorized",
                "UNAUTHORIZED",
                request.getRequestURI()
        );

        new ObjectMapper().writeValue(response.getOutputStream(), error);
    }
}