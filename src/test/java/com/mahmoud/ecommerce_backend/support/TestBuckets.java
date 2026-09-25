package com.mahmoud.ecommerce_backend.support;

import java.lang.reflect.Field;

import com.github.benmanes.caffeine.cache.Cache;
import com.mahmoud.ecommerce_backend.security.config.RateLimitFilter;

import io.github.bucket4j.Bucket;

/**
 * Test-only helper: clears the in-memory Bucket4j rate-limit buckets of the
 * shared {@link RateLimitFilter} so every test class starts from a full
 * budget regardless of execution order.
 *
 * Test infrastructure only — never referenced by production code and does
 * not alter production rate-limit capacity or behavior. Reflection is
 * required because the Caffeine cache field is intentionally private; the
 * bucket-reset pattern already existed inline in
 * RefreshTokenLifecycleRegressionTest / PasswordFlowRegressionTest /
 * IdorRegressionTest before this utility was extracted.
 */
public final class TestBuckets {

    private TestBuckets() {
    }

    /** Drop every stored bucket so subsequent auth POSTs start unconsumed. */
    public static void reset(RateLimitFilter rateLimitFilter) {
        try {
            Field field = RateLimitFilter.class.getDeclaredField("buckets");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Cache<String, Bucket> cache = (Cache<String, Bucket>) field.get(rateLimitFilter);
            if (cache != null) {
                cache.invalidateAll();
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalStateException("Could not reset rate-limit buckets", ex);
        }
    }
}
