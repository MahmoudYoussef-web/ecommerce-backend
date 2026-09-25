package com.mahmoud.ecommerce_backend.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.exception.ApiErrorResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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
import java.time.Duration;
import java.util.Set;

/**
 * Per-IP, per-endpoint rate limiting (Bucket4j) for password-based and
 * session-bootstrapping endpoints: login, register, refresh.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password"
    );

    /**
     * Bounded + expiring bucket storage. Previously an unbounded
     * ConcurrentHashMap let attackers grow heap indefinitely by rotating
     * spoofed client keys. Entries expire after 15 minutes without use and
     * the cache is hard-capped at 100k keys (evicts least-recently-used).
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.enabled}")
    private boolean enabled;

    @Value("${app.rate-limit.capacity}")
    private int capacity;

    @Value("${app.rate-limit.refill-per-minute}")
    private int refillPerMinute;

    /**
     * Only set true behind a trusted reverse proxy that OVERWRITES
     * X-Forwarded-For. When false (default, direct exposure), forwarded
     * headers are ignored entirely so clients cannot rotate spoofed IPs to
     * bypass throttling.
     */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled || !isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + "|" + request.getRequestURI();
        Bucket bucket = buckets.get(key, k -> newBucket());

        if (!bucket.tryConsume(1)) {
            writeTooManyRequests(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isProtected(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return PROTECTED_PATHS.contains(request.getRequestURI());
    }

    /**
     * Resolves the throttling identity. With trust-proxy=false (default) the
     * TCP peer address is used and X-Forwarded-For is deliberately ignored —
     * a client-supplied header must never decide who gets throttled. With
     * trust-proxy=true (behind a trusted proxy that overwrites the header),
     * the first forwarded hop is used.
     */
    private String clientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }

    private void writeTooManyRequests(HttpServletResponse response,
                                      HttpServletRequest request) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = new ApiErrorResponse(
                429,
                "Too many requests. Please try again later.",
                "RATE_LIMITED",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
