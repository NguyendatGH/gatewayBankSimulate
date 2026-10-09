package com.bankSimulate.mockbank;

import com.bankSimulate.domain.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ThreeDsMockBank implements MockBank {

    public static final String SANDBOX_OTP = "123456";
    private static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(ThreeDsMockBank.class);

    private final MockBankSessionStore sessions;

    public ThreeDsMockBank(MockBankSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public MockBankDecision authorize(MockBankAuthorizeInput input) {
        String bankRef = "TDS-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String last4 = input.panLast4();
        var preference = input.authPreference() == null
                ? MockBankAuthorizeInput.AuthPreference.NONE : input.authPreference();

        if (!"1000".equals(last4) && !"2000".equals(last4)) {
            log.info("declined ref={} last4={}", bankRef, last4);
            return MockBankDecision.decline(bankRef, "Card not accepted by issuer");
        }

        boolean riskyCard = "1000".equals(last4);
        boolean challenge = riskyCard || preference == MockBankAuthorizeInput.AuthPreference.CHALLENGE;

        if (!challenge) {
            log.info("approved without authentication ref={} last4={} preference={}",
                    bankRef, last4, preference);
            return MockBankDecision.approve(bankRef, authCode());
        }
        if (riskyCard && preference == MockBankAuthorizeInput.AuthPreference.SKIP) {
            log.info("merchant asked to skip authentication but issuer risk rules override it"
                    + " ref={} last4={}", bankRef, last4);
        }
        sessions.put(new MockBankSessionStore.Session(bankRef, input.reference(), input.amount(), input.currency(),
                last4, input.notifyUrl(), input.termUrl()));
        log.info("cardholder authentication required ref={} last4={} preference={}",
                bankRef, last4, preference);
        return MockBankDecision.challenge(bankRef);
    }

    @Override
    public MockBankDecision authenticate(String bankRef, String otp) {
        MockBankSessionStore.Session session = sessions.find(bankRef)
                .orElseThrow(() -> new ApiException(404, "AUTH_SESSION_NOT_FOUND", "Authentication session not found"));
        if (session.isClosed()) throw new ApiException(409, "AUTH_SESSION_CLOSED", "Authentication session is closed");

        if (SANDBOX_OTP.equals(otp)) {
            session.close();
            log.info("cardholder authenticated ref={}", bankRef);
            return MockBankDecision.approve(bankRef, authCode());
        }
        int attempts = session.registerFailedAttempt();
        if (attempts >= MAX_ATTEMPTS) {
            session.close();
            log.info("authentication failed after {} attempts ref={}", attempts, bankRef);
            return MockBankDecision.decline(bankRef, "Authentication failed after " + attempts + " attempts");
        }
        throw new ApiException(400, "OTP_INVALID", "Còn " + (MAX_ATTEMPTS - attempts) + " lần thử");
    }

    public int remainingAttempts(String bankRef) {
        return sessions.find(bankRef).map(s -> MAX_ATTEMPTS - s.attempts()).orElse(0);
    }

    private static String authCode() {
        return "AUTH" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
