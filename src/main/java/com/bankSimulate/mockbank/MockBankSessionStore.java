package com.bankSimulate.mockbank;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockBankSessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void put(Session session) {
        sessions.put(session.bankRef(), session);
    }

    public Optional<Session> find(String bankRef) {
        return Optional.ofNullable(sessions.get(bankRef));
    }

    public void remove(String bankRef) {
        sessions.remove(bankRef);
    }

    public static final class Session {
        private final String bankRef;
        private final String reference;
        private final long amount;
        private final String currency;
        private final String panLast4;
        private final String notifyUrl;
        private final String termUrl;
        private int attempts;
        private boolean closed;

        public Session(String bankRef, String reference, long amount, String currency, String panLast4,
                       String notifyUrl, String termUrl) {
            this.bankRef = bankRef;
            this.reference = reference;
            this.amount = amount;
            this.currency = currency;
            this.panLast4 = panLast4;
            this.notifyUrl = notifyUrl;
            this.termUrl = termUrl;
        }

        public String bankRef() { return bankRef; }

        public String reference() { return reference; }
        public long amount() { return amount; }
        public String currency() { return currency; }
        public String panLast4() { return panLast4; }
        public String notifyUrl() { return notifyUrl; }
        public String termUrl() { return termUrl; }
        public int attempts() { return attempts; }
        public int registerFailedAttempt() { return ++attempts; }
        public boolean isClosed() { return closed; }
        public void close() { this.closed = true; }
    }
}
