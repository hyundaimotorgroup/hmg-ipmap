package com.hmg.ipmap.common.handler;

import com.hmg.ipmap.common.util.UuidUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE) // Trace-id harus paling awal
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-HMGIPMAP-TRACEID";
    public static final String TRACE_ID_MDC_KEY = "trace_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UuidUtil.generateUuid();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            response.setHeader(TRACE_ID_HEADER, traceId);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
