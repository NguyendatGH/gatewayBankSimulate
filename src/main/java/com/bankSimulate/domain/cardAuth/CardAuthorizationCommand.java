package com.bankSimulate.domain.cardAuth;

import com.bankSimulate.domain.enums.ThreeDsPreference;

public record CardAuthorizationCommand(
        String gwTxnId, String merchantNo, long amount, String currency,
        String pan, int expiryMonth, int expiryYear, String cvv,
        ThreeDsPreference threeDsPreference,
        String notifyUrl, String termUrl
) {
}
