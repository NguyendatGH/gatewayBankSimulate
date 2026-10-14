package com.bankSimulate.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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

    public void close(String paymentId) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(PREFIX + paymentId, Instant.now().toString(), ttl);
            if (Boolean.TRUE.equals(first))
                log.info("Checkout link closed id={} (blocked for {})", paymentId, ttl);
            else
                log.info("Checkout link already closed id={}, keep the first close time", paymentId);
        } catch (Exception err) {
            log.warn("Redis unavailable, cannot close checkout id={}: {}", paymentId, err.getMessage());
        }
    }

    public boolean isClosed(String paymentId) {
        return closedAt(paymentId).isPresent();
    }

    public Optional<String> closedAt(String paymentId) {
        try {
            String value = redis.opsForValue().get(PREFIX + paymentId);
            if (value == null) return Optional.empty();
            return Optional.of("1".equals(value) ? "unknown" : value);
        } catch (Exception err) {
            log.warn("Redis unavailable, checkout guard skipped id={}: {}", paymentId, err.getMessage());
            return Optional.empty();
        }
    }
}
