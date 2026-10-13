package com.bankSimulate.mockbank;

public record MockBankAuthorizeInput(String reference, long amount, String currency,
                                     String pan, int expiryMonth, int expiryYear, String cvv,
                                     AuthPreference authPreference,
                                     String notifyUrl, String termUrl) {

    public enum AuthPreference { NONE, CHALLENGE, SKIP }

    public String panLast4() {
        return pan == null || pan.length() < 4 ? "" : pan.substring(pan.length() - 4);
    }
}
