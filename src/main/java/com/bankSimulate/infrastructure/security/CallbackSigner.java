package com.bankSimulate.infrastructure.security;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CallbackSigner {
    private static final Logger log = LoggerFactory.getLogger(CallbackSigner.class);
    private static final long MAX_AGE_SECONDS = 300;
    private final Environment env;

    public CallbackSigner(Environment env) {
        this.env = env;
    }

    private static String msg(String bank, String timestamp, String rawBody) {
        return bank + "\n" + timestamp + "\n" + rawBody;
    }

    private String secretFor(String bank) {
        if (bank == null || !bank.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid bank code");
        String secret = env.getProperty("gateway.bank-callback-secrets." + bank);
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("Bank callback secret is not configured for " + bank);
        return secret;
    }

    public String sign(String bank, String timestamp, String rawBody) {
        return HmacSha256.hex(msg(bank, timestamp, rawBody), secretFor(bank));
    }

    public boolean matches(String bank, String timestamp,
            String rawBody, String suppliedHex) {
        if (bank == null || !bank.matches("[A-Za-z0-9_-]+")) {
            log.warn("Callback rejected: invalid bank code");
            return false;
        }
        if (timestamp == null || !timestamp.matches("[0-9]{10,12}")) {
            log.warn("Callback rejected: missing or invalid X-Bank-Timestamp (bank={})", bank);
            return false;
        }
        try {
            long sentAt = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - sentAt) > MAX_AGE_SECONDS) {
                log.warn("Callback rejected: timestamp is {}s off, max {}s (bank={})",
                        now - sentAt, MAX_AGE_SECONDS, bank);
                return false;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        boolean ok = HmacSha256.matches(msg(bank, timestamp, rawBody),
                secretFor(bank), suppliedHex);
        if (ok)
            log.info("Callback signature verified with {} key, sig={}...", bank, suppliedHex.substring(0, 8));
        else
            log.warn("Callback rejected: signature does not match {} key", bank);
        return ok;
    }
}
