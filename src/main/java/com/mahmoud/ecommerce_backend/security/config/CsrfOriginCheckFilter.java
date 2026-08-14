package com.mahmoud.ecommerce_backend.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Origin-header CSRF defense for cookie-authenticated endpoints.
 *
 * The refresh/logout endpoints authenticate via the httpOnly refresh cookie,
 * so a cross-site attacker could otherwise trigger state changes. Every
 * state-changing request to these endpoints must carry an Origin header (or,
 * failing that, a Referer) whose origin matches an explicitly allowed origin.
 */
@Component
@RequiredArgsConstructor
public class CsrfOriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_AUTH_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!requiresOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin == null) {
            String referer = request.getHeader("Referer");
            if (referer != null) {
                origin = extractOrigin(referer);
            }
        }

        if (origin == null || !isAllowed(origin)) {
            writeForbidden(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresOrigin(HttpServletRequest request) {
        String method = request.getMethod();
        boolean stateChanging = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);

        return stateChanging && COOKIE_AUTH_PATHS.contains(request.getRequestURI());
    }

    private boolean isAllowed(String origin) {
        String normalized = normalizeOrigin(origin);
        if (normalized == null) {
            return false;
        }
        return allowedOrigins().stream()
                .anyMatch(normalized::equals);
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            return port == -1
                    ? scheme + "://" + host
                    : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Set<String> allowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizeOrigin)
                .collect(Collectors.toSet());
    }

    private String extractOrigin(String referer) {
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeForbidden(HttpServletResponse response,
                                HttpServletRequest request) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = new ApiErrorResponse(
                403,
                "Cross-site request rejected (CSRF check failed)",
                "CSRF_REJECTED",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
