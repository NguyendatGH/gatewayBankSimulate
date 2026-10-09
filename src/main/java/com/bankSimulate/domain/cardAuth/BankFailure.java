package com.bankSimulate.domain.cardAuth;

public final class BankFailure {

    public static final String UNREACHABLE = "BANK_UNREACHABLE";

    public static final String TIMEOUT = "BANK_TIMEOUT";

    public static final String EMPTY_RESPONSE = "BANK_EMPTY_RESPONSE";

    public static final String CALL_REJECTED = "BANK_CALL_REJECTED";

    public static final String ALL_UNAVAILABLE = "ALL_CARD_BANKS_UNAVAILABLE";

    private BankFailure() {}

    public static boolean canTryAnotherBank(String errorCode) {
        return UNREACHABLE.equals(errorCode);
    }
}
