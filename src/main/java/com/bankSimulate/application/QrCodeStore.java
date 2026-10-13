package com.bankSimulate.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

@Component
public class QrCodeStore {
    private static final Logger log = LoggerFactory.getLogger(QrCodeStore.class);
    private static final String PREFIX = "qr:current:";

    private final StringRedisTemplate redis;
    private final Duration qrTtl;

    public QrCodeStore(StringRedisTemplate redis, @Value("${gateway.qr.code-ttl:PT2M}") Duration qrTtl) {
        this.redis = redis;
        this.qrTtl = qrTtl;
    }

    public record Ticket(String qrId, Instant expiresAt) {
    }

    public Ticket current(String tradeNo) {
        String key = PREFIX + tradeNo;
        String qrId = redis.opsForValue().get(key);
        if (qrId == null)
            return null;
        Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0)
            return null;
        return new Ticket(qrId, Instant.now().plusSeconds(seconds));
    }

    public Ticket issue(String tradeNo, Instant sessionExpiresAt) {
        Duration left = Duration.between(Instant.now(), sessionExpiresAt);
        Duration ttl = left.compareTo(qrTtl) < 0 ? left : qrTtl;
        if (ttl.isNegative() || ttl.isZero())
            return null;

        String qrId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        redis.opsForValue().set(PREFIX + tradeNo, qrId, ttl);
        log.info("QR issued tradeNo={} qrId={} ttl={}s", tradeNo, qrId, ttl.toSeconds());
        return new Ticket(qrId, Instant.now().plus(ttl));
    }

    /** Mã được quét có phải mã mới nhất và còn hạn không. */
    public boolean isCurrent(String tradeNo, String qrId) {
        if (qrId == null)
            return false;
        try {
            return qrId.equals(redis.opsForValue().get(PREFIX + tradeNo));
        } catch (Exception e) {
            log.warn("Redis unavailable, QR check skipped tradeNo={}: {}", tradeNo, e.getMessage());
            return true;
        }
    }

    /**
     * Xóa mã QR khi giao dịch đã có kết quả. Được gọi SAU khi DB đã commit và TRƯỚC khi gửi webhook,
     * nên nếu Redis lỗi mà ném exception thì giao dịch đã capture nhưng webhook không bao giờ được gửi.
     * Vì vậy chỉ ghi log, không ném lỗi (key sẽ tự hết hạn theo TTL).
     */
    public void revoke(String tradeNo) {
        try {
            redis.delete(PREFIX + tradeNo);
        } catch (Exception e) {
            log.warn("Redis unavailable, cannot revoke QR tradeNo={}: {}", tradeNo, e.getMessage());
        }
    }
}
