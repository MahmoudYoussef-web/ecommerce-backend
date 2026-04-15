package com.mahmoud.ecommerce_backend.logging;

import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

public class LoggingContextUtil {

    public static void enrich() {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal user) {
            MDC.put("userId", String.valueOf(user.getUserId()));
        }

        if (TenantContext.isSet()) {
            MDC.put("tenantId", String.valueOf(TenantContext.getRequired()));
        }
    }

    public static void clear() {
        MDC.remove("userId");
        MDC.remove("tenantId");
    }
}