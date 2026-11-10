package com.ktb.moyeota.global.trace;

import io.sentry.Sentry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceId.HEADER);
        if (traceId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Sentry.setTag(TraceId.SENTRY_TAG, traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(TraceId.LOG_KEY, traceId)) {
            filterChain.doFilter(request, response);
        }
    }
}
