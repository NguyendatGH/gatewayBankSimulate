package com.bankSimulate.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SANE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Pattern TRADE_NO_IN_PATH = Pattern.compile("/(TradeNo[0-9]{6,18})(?=/|$)");

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.clear();
        String incoming = sane(request.getHeader(HEADER));
        String requestId = incoming != null ? incoming : UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        long startedAt = System.nanoTime();

        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(sane(request.getHeader("X-Merchant-No")),
                sane(request.getHeader("X-Terminal-Id")), null)) {
            MDC.put(MDC_KEY, requestId);
            Matcher tradeNo = TRADE_NO_IN_PATH.matcher(request.getRequestURI());
            if (tradeNo.find()) GatewayLogContext.setTradeNo(tradeNo.group(1));
            log.info("<- {} {}", request.getMethod(), request.getRequestURI());
            try {
                chain.doFilter(request, response);
            } finally {
                log.info("-> {} in {}ms", response.getStatus(), (System.nanoTime() - startedAt) / 1_000_000);
            }
        }
    }

    private static String sane(String value) {
        return value != null && SANE.matcher(value).matches() ? value : null;
    }
}
