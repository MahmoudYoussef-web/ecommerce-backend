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

        String traceId = UUID.randomUUID().toString();

        try {
            MDC.put("traceId", traceId);

            // Echo the correlation id so clients can reference it in bug
            // reports; it matches the traceId field in every JSON log line.
            if (response instanceof jakarta.servlet.http.HttpServletResponse httpResponse) {
                httpResponse.setHeader("X-Trace-Id", traceId);
            }

            LoggingContextUtil.enrich();

            chain.doFilter(request, response);

        } finally {
            MDC.clear();

        }
    }
}