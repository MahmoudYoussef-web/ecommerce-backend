package com.mahmoud.ecommerce_backend.security.jwt;

import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import com.mahmoud.ecommerce_backend.security.user.ShopUserDetailsService;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final ShopUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtils.validate(jwt)) {

                String email = jwtUtils.extractEmail(jwt);
                Long userId = jwtUtils.extractUserId(jwt);
                Integer tokenVersion = jwtUtils.extractTokenVersion(jwt);
                Long tenantId = jwtUtils.extractTenantId(jwt);

                var userDetails = userDetailsService.loadUserByUsername(email);

                if (userDetails instanceof CustomUserPrincipal principal) {

                    if (!principal.getUserId().equals(userId) ||
                            !principal.getTokenVersion().equals(tokenVersion)) {

                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    TenantContext.set(tenantId);

                    if (SecurityContextHolder.getContext().getAuthentication() == null) {

                        var auth = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );

                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);


        TenantContext.clear();
    }

    private String parseJwt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer "))
                ? header.substring(7)
                : null;
    }
}