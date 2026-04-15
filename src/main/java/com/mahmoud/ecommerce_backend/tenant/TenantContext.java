package com.mahmoud.ecommerce_backend.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("Invalid tenantId");
        }
        CONTEXT.set(tenantId);
    }

    public static Long getRequired() {
        Long tenantId = CONTEXT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context missing");
        }
        return tenantId;
    }

    public static Long getOrNull() {
        return CONTEXT.get();
    }

    public static boolean isSet() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}