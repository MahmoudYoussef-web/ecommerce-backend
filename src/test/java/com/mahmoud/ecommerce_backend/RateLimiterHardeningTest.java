package com.mahmoud.ecommerce_backend;

import com.github.benmanes.caffeine.cache.Cache;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;
import com.mahmoud.ecommerce_backend.support.TestBuckets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 regression:
 *  - client-supplied X-Forwarded-For must NOT let attackers rotate throttle
 *    identities (trust-proxy defaults to false),
 *  - bucket storage must be bounded/expiring (Caffeine), not an unbounded map.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimiterHardeningTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void resetRateLimitBuckets() {
        // Probe loops below rely on hitting the limit within 15 requests -
        // start from an untouched bucket regardless of class order.
        TestBuckets.reset(rateLimitFilter);
    }

    @Test
    void spoofedForwardedHeadersCannotRotateThrottleIdentity() {
        // trust-proxy must default to false: the throttling key comes from the
        // TCP peer, so rotating X-Forwarded-For values cannot bypass limits.
        assertThat(ReflectionTestUtils.getField(rateLimitFilter, "trustProxy"))
                .isEqualTo(false);

        HttpHeaders base = new HttpHeaders();
        base.setOrigin("http://localhost:5173");
        base.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        int tooMany = 0;
        for (int i = 0; i < 15; i++) {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(base);
            // Every request claims a different client IP.
            headers.set("X-Forwarded-For", "10.0." + (i / 255) + "." + (i % 255) + ", 10.9.9.9");

            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + "/api/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (resp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                tooMany++;
            }
        }

        assertThat(tooMany)
                .as("unique spoofed XFF values must not bypass per-IP throttling")
                .isGreaterThan(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bucketStorageIsBoundedAndExpiring() {
        Object field = ReflectionTestUtils.getField(rateLimitFilter, "buckets");
        assertThat(field).isInstanceOf(Cache.class);

        Cache<String, ?> cache = (Cache<String, ?>) field;

        // Hard cap present (no unbounded growth).
        assertThat(cache.policy().eviction().isPresent()).isTrue();
        assertThat(cache.policy().eviction().get().getMaximum()).isEqualTo(100_000L);

        // Expiry configured: entries vanish after inactivity.
        assertThat(cache.policy().expireAfterAccess().isPresent()).isTrue();
    }

    @Test
    void sameClientIsStillThrottled() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        int tooMany = 0;
        for (int i = 0; i < 15; i++) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + "/api/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (resp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                tooMany++;
            }
        }

        assertThat(tooMany).isGreaterThan(0);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
