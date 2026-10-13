package com.bankSimulate.infrastructure.scheduling;

import com.bankSimulate.application.CardPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class CardTransactionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CardTransactionExpiryJob.class);

    private final CardPaymentService service;

    public CardTransactionExpiryJob(CardPaymentService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${gateway.card.expiry-interval:PT60S}", initialDelayString = "PT30S")
    public void run() {
        try {
            int expired = service.expireStale();
            if (expired > 0) log.info("Expired {} stale card transaction(s)", expired);
        } catch (RuntimeException ex) {
            log.error("Card expiry job failed", ex);
        }
    }
}
