package com.insurance.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs one structured line per HTTP request (method, path, status, duration) and stamps a
 * "requestId" onto the SLF4J MDC for the lifetime of the request, so every log line emitted
 * while handling that request — from controllers, services, or the exception handler — can be
 * correlated back to it. Shows up automatically in both the dev console pattern and the prod
 * JSON encoder (see logback-spring.xml).
 *
 * Runs before Spring Security's filters so the request id is available even for requests that
 * get rejected by auth.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String METHOD_MDC_KEY = "method";
    private static final String PATH_MDC_KEY = "path";
    private static final String STATUS_MDC_KEY = "status";
    private static final String DURATION_MDC_KEY = "durationMs";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long start = System.currentTimeMillis();

        try {
            org.slf4j.MDC.put(REQUEST_ID_MDC_KEY, requestId);
            org.slf4j.MDC.put(METHOD_MDC_KEY, request.getMethod());
            org.slf4j.MDC.put(PATH_MDC_KEY, request.getRequestURI());
            response.setHeader(REQUEST_ID_HEADER, requestId);

            log.debug("--> {} {}", request.getMethod(), request.getRequestURI());

            chain.doFilter(request, response);

        } finally {
            long durationMs = System.currentTimeMillis() - start;
            org.slf4j.MDC.put(STATUS_MDC_KEY, String.valueOf(response.getStatus()));
            org.slf4j.MDC.put(DURATION_MDC_KEY, String.valueOf(durationMs));

            if (response.getStatus() >= 500) {
                log.error("<-- {} {} {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), durationMs);
            } else if (response.getStatus() >= 400) {
                log.warn("<-- {} {} {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), durationMs);
            } else {
                log.info("<-- {} {} {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), durationMs);
            }

            org.slf4j.MDC.clear();
        }
    }

    /** Don't log noisy static asset / actuator requests if any get added later. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }
}
