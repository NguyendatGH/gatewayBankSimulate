package com.bankSimulate.mockbank.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class MockBankADtos {

    private MockBankADtos() {}

    public static final String PREF_CHALLENGE = "CHALLENGE";
    public static final String PREF_SKIP = "SKIP";

    public static final String RESP_APPROVED = "00";
    public static final String RESP_AUTH_REQUIRED = "05";
    public static final String RESP_DECLINED = "99";

    public record AuthorizeRequest(@NotBlank String merchantId, @NotBlank String txnRef,
                                   @NotBlank String txnAmount, @NotBlank String ccy,
                                   @NotNull @Valid Card card,
                                   String authPreference,
                                   @NotBlank String notifyUrl, @NotBlank String termUrl) {}

    public record Card(@NotBlank String number, @NotBlank String expiry, @NotBlank String cvv) {}

    public record AuthorizeResponse(String respCode, String respMsg, String txnId, String authUrl) {}

    public record Notification(String txnId, String respCode, String respMsg, String authCode) {}
}
