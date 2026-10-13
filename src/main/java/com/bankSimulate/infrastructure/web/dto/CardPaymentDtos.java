package com.bankSimulate.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class CardPaymentDtos {

    private CardPaymentDtos() {}

    public record CreateRequest(@NotBlank String orderCode,
                                @Positive long amount,
                                @Pattern(regexp = "[0-9]{13,19}", message = "Số thẻ gồm 13–19 chữ số") String pan,
                                Integer expiryMonth,
                                Integer expiryYear,
                                @Pattern(regexp = "[0-9]{3,4}", message = "CVV gồm 3–4 chữ số") String cvv,
                                @NotBlank String returnUrl,
                                String webhookUrl) {

        public boolean hasCard() {
            return pan != null && !pan.isBlank() && cvv != null && !cvv.isBlank()
                    && expiryMonth != null && expiryYear != null;
        }
    }

    public record CreateResponse(String gwTxnId, String resultCode, String status, String checkoutUrl,
                                 String redirectUrl, String bankRef, String cardMasked, String message) {}

    public record StatusResponse(String gwTxnId, String orderCode, long amount, String resultCode, String status,
                                 String bankCode, String bankRef, String authCode, String cardBrand,
                                 String cardMasked, String failureReason, java.time.Instant paidAt) {}
}
