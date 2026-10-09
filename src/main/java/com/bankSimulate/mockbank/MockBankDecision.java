package com.bankSimulate.mockbank;

public record MockBankDecision(Kind kind, String bankRef, String authCode, String message) {

    public enum Kind {
        APPROVE,
        DECLINE,
        CHALLENGE
    }

    public static MockBankDecision approve(String bankRef, String authCode) {
        return new MockBankDecision(Kind.APPROVE, bankRef, authCode, "Approved");
    }

    public static MockBankDecision decline(String bankRef, String message) {
        return new MockBankDecision(Kind.DECLINE, bankRef, null, message);
    }

    public static MockBankDecision challenge(String bankRef) {
        return new MockBankDecision(Kind.CHALLENGE, bankRef, null, "Cardholder authentication required");
    }
}
