package com.mahmoud.ecommerce_backend.security.jwt;

import com.mahmoud.ecommerce_backend.security.user.ShopUserDetailsService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
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

            if (jwt != null) {

                if (!jwtUtils.validate(jwt)) {
                    log.warn("Invalid JWT token");
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = jwtUtils.extractUsername(jwt);

                if (email == null) {
                    log.warn("JWT does not contain subject");
                    filterChain.doFilter(request, response);
                    return;
                }

                if (SecurityContextHolder.getContext().getAuthentication() == null) {

                    var userDetails = userDetailsService.loadUserByUsername(email);

                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(auth);

                    log.debug("Authenticated user: {}", email);
                }
            }

        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
            SecurityContextHolder.clearContext();

        } catch (JwtException ex) {
            log.warn("JWT error: {}", ex.getMessage());
            SecurityContextHolder.clearContext();

        } catch (Exception ex) {
            log.error("Unexpected authentication error", ex);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null) {
            return null;
        }

        if (!header.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format");
            return null;
        }

        return header.substring(7);
    }
}