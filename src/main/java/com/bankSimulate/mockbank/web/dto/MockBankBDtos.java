package com.bankSimulate.mockbank.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;


public final class MockBankBDtos {

    private MockBankBDtos() {}

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DECLINED = "DECLINED";

    public record AuthorizeRequest(@NotBlank String merchant, @NotBlank String reference,
                                   @Positive long amount, @NotBlank String currency,
                                   @NotBlank String pan, int expMonth, int expYear, @NotBlank String cvv,
                                   String callback) {}

    public record AuthorizeResponse(String status, String bankTransactionId, String message) {}
}
