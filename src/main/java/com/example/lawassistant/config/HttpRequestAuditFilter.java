package com.example.lawassistant.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HttpRequestAuditFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestAuditFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.info("event=http_request payload={}", requestPayload(
                    request.getMethod(),
                    safePath(request),
                    response.getStatus(),
                    elapsedMs(start)
            ));
        }
    }

    String requestPayload(String method, String path, int status, long elapsedMs) {
        try {
            return OBJECT_MAPPER.writeValueAsString(requestFields(method, path, status, elapsedMs));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize HTTP request audit payload", e);
        }
    }

    Map<String, Object> requestFields(String method, String path, int status, long elapsedMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("method", method);
        fields.put("path", path);
        fields.put("status", status);
        fields.put("elapsedMs", elapsedMs);
        return fields;
    }

    String safePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null ? "" : uri;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
