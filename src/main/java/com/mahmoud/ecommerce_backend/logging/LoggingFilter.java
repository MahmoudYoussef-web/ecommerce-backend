package com.mahmoud.ecommerce_backend.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class LoggingFilter implements Filter {

    private static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        try {
            MDC.put("traceId", UUID.randomUUID().toString());

            LoggingContextUtil.enrich();

            chain.doFilter(request, response);

        } finally {
            MDC.clear();

        }
    }
}