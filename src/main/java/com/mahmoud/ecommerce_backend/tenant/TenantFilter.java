package com.mahmoud.ecommerce_backend.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.entity.Tenant;
import com.mahmoud.ecommerce_backend.exception.ApiErrorResponse;
import com.mahmoud.ecommerce_backend.repository.TenantRepository;
import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long tenantId = principal.getTenantId();

        if (tenantId == null) {
            writeError(response, request, 401, "Tenant context missing", "TENANT_MISSING");
            return;
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        if (tenant == null) {
            writeError(response, request, 401, "Tenant not found", "TENANT_NOT_FOUND");
            return;
        }

        if (!tenant.isActive()) {
            writeError(response, request, 403, "Tenant inactive", "TENANT_INACTIVE");
            return;
        }

        TenantContext.set(tenantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            int status,
                            String message,
                            String errorCode) throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = new ApiErrorResponse(
                status,
                message,
                errorCode,
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

}
