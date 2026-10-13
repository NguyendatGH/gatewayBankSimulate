package com.bankSimulate.infrastructure.scheduling;

import com.bankSimulate.application.QrPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class QrPaymentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(QrPaymentExpiryJob.class);

    private final QrPaymentService service;

    public QrPaymentExpiryJob(QrPaymentService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${gateway.qr.expiry-interval:PT60S}", initialDelayString = "PT30S")
    public void run() {
        try {
            int expired = service.expireStale();
            if (expired > 0) log.info("Expired {} stale QR/wallet payment(s)", expired);
        } catch (RuntimeException ex) {
            log.error("QR expiry job failed", ex);
        }
    }
}
