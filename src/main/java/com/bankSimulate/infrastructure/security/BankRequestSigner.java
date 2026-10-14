package com.bankSimulate.infrastructure.security;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.bankSimulate.domain.common.ApiException;

/**
 * Ký / kiểm lệnh Gateway gửi sang Mock Bank (chiều ngược với {@link CallbackSigner}).
 *
 * Dùng key riêng gateway.bank-request-secrets.{BANK}, KHÔNG dùng chung key callback: lộ key một chiều
 * thì chiều kia vẫn an toàn. Chuỗi ký có thêm chữ "authorize" ở đầu để chữ ký lệnh không bao giờ trùng
 * dạng với chữ ký callback.
 */
@Component
public class BankRequestSigner {
    public static final String TIMESTAMP_HEADER = "X-Gateway-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Gateway-Signature";

    private static final Logger log = LoggerFactory.getLogger(BankRequestSigner.class);
    private static final long MAX_AGE_SECONDS = 300;
    private final Environment env;

    public BankRequestSigner(Environment env) {
        this.env = env;
    }

    /** Bên Gateway: ký raw body sẽ gửi đi. */
    public Signed sign(String bank, String rawBody) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = HmacSha256.hex(msg(bank, timestamp, rawBody), secretFor(bank));
        log.info("Signed authorize request with {} request key: HMAC-SHA256(bank + timestamp {} + body {} bytes) sig={}...",
                bank, timestamp, rawBody.length(), signature.substring(0, 8));
        return new Signed(timestamp, signature);
    }

    /** Bên Mock Bank: kiểm trước khi parse body; sai thì 401. */
    public void requireValid(String bank, String timestamp, String rawBody, String suppliedHex) {
        if (!matches(bank, timestamp, rawBody, suppliedHex))
            throw new ApiException(401, "INVALID_GATEWAY_SIGNATURE", "Gateway request signature does not match");
    }

    private boolean matches(String bank, String timestamp, String rawBody, String suppliedHex) {
        if (bank == null || !bank.matches("[A-Za-z0-9_-]+")) {
            log.warn("Authorize request rejected: invalid bank code");
            return false;
        }
        if (timestamp == null || !timestamp.matches("[0-9]{10,12}")) {
            log.warn("Authorize request rejected: missing or invalid {} (bank={})", TIMESTAMP_HEADER, bank);
            return false;
        }
        long age = Instant.now().getEpochSecond() - Long.parseLong(timestamp);
        if (Math.abs(age) > MAX_AGE_SECONDS) {
            log.warn("Authorize request rejected: timestamp is {}s off, max {}s (bank={})", age, MAX_AGE_SECONDS, bank);
            return false;
        }
        boolean ok = HmacSha256.matches(msg(bank, timestamp, rawBody), secretFor(bank), suppliedHex);
        if (ok)
            log.info("Authorize request signature verified with {} request key, sig={}...", bank, suppliedHex.substring(0, 8));
        else
            log.warn("Authorize request rejected: signature does not match {} request key", bank);
        return ok;
    }

    private String secretFor(String bank) {
        if (bank == null || !bank.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid bank code");
        String secret = env.getProperty("gateway.bank-request-secrets." + bank);
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("Bank request secret is not configured for " + bank);
        return secret;
    }

    private static String msg(String bank, String timestamp, String rawBody) {
        return "authorize\n" + bank + "\n" + timestamp + "\n" + rawBody;
    }

    public record Signed(String timestamp, String signature) {
    }
}
