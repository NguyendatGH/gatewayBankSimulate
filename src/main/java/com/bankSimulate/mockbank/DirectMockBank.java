package com.bankSimulate.mockbank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DirectMockBank implements MockBank {

    private static final Logger log = LoggerFactory.getLogger(DirectMockBank.class);

    @Override
    public MockBankDecision authorize(MockBankAuthorizeInput input) {
        String bankRef = "DIR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        if ("1000".equals(input.panLast4())) {
            log.info("approved ref={} last4={}", bankRef, input.panLast4());
            return MockBankDecision.approve(bankRef, "AUTH" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }
        log.info("declined ref={} last4={}", bankRef, input.panLast4());
        return MockBankDecision.decline(bankRef, "Insufficient funds or card blocked");
    }
}
