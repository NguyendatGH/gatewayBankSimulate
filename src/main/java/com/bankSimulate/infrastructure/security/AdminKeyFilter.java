package com.bankSimulate.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;
import com.bankSimulate.infrastructure.logging.RequestIdFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminKeyFilter extends OncePerRequestFilter {
    private final String configuredKey;
    public AdminKeyFilter(@Value("${gateway.admin-key:}") String configuredKey) { this.configuredKey = configuredKey; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/admin/")) {
            String supplied = request.getHeader("X-Admin-Key");
            if (configuredKey.isBlank() || supplied == null || !java.security.MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401); response.setContentType("application/problem+json");
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Invalid admin key\",\"status\":401,\"code\":\"INVALID_ADMIN_KEY\",\"traceId\":\"" + MDC.get(RequestIdFilter.MDC_KEY) + "\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
