package com.bankSimulate.domain.cardAuth;

import com.bankSimulate.domain.enums.Outcome;

public record CardAuthorizationResult(Outcome outcome, String bankRef, String redirectUrl, String authCode,
                                      String bankRawCode, String msg) {

    public static CardAuthorizationResult approved(String bankRef, String authCode, String rawCode, String message) {
        return new CardAuthorizationResult(Outcome.APPROVED, bankRef, null, authCode, rawCode, message);
    }

    public static CardAuthorizationResult declined(String bankRef, String rawCode, String message) {
        return new CardAuthorizationResult(Outcome.DECLINED, bankRef, null, null, rawCode, message);
    }

    public static CardAuthorizationResult redirect(String bankRef, String redirectUrl, String rawCode, String message) {
        return new CardAuthorizationResult(Outcome.REDIRECT_REQUIRED, bankRef, redirectUrl, null, rawCode, message);
    }
}
