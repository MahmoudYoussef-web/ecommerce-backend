package com.mahmoud.ecommerce_backend.tenant;

import com.mahmoud.ecommerce_backend.entity.Tenant;
import com.mahmoud.ecommerce_backend.repository.TenantRepository;
import com.mahmoud.ecommerce_backend.security.jwt.JwtUtils;
import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
                filterChain.doFilter(request, response);
                return;
            }

            Long tenantId = principal.getTenantId();

            if (tenantId == null) {
                throw new RuntimeException("Tenant missing");
            }

            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found"));

            if (!tenant.isActive()) {
                throw new RuntimeException("Tenant inactive");
            }

            TenantContext.set(tenantId);

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

}