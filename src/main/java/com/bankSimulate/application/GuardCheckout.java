package com.bankSimulate.application;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class GuardCheckout {
    private static final Logger log = LoggerFactory.getLogger(GuardCheckout.class);
    private static final String PREFIX = "checkout:closed:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public GuardCheckout(StringRedisTemplate redis, @Value("${gateway.checkout.closed-ttl:PT24H}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /** Gọi ngay trước khi redirect user về returnUrl/cancelUrl. */
    public void close(String paymentId) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(PREFIX + paymentId, "1", ttl);
            log.info("Checkout closed id={} firstTime={}", paymentId, first);
        } catch (Exception err) {
            log.warn("Redis unavailable, cannot close checkout id={}: {}", paymentId, err.getMessage());
        }
    }

    /** true = đã redirect rồi, chặn truy cập. */
    public boolean isClosed(String paymentId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + paymentId));
        } catch (Exception err) {
            log.warn("Redis unavailable, checkout guard skipped id={}: {}", paymentId, err.getMessage());
            return false;
        }
    }
}
