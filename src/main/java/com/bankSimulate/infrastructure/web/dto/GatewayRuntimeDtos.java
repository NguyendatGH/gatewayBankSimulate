package com.bankSimulate.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

public final class GatewayRuntimeDtos {
    private GatewayRuntimeDtos() {}

    public record CreatePaymentRequest(@Positive long orderCode, @Positive long amount, @NotBlank String description,
                                       String paymentMethod, Boolean threeDs,
                                       @NotEmpty List<@Valid Item> items, @NotBlank String returnUrl,
                                       @NotBlank String cancelUrl, Instant expiresAt,
                                       String settlementBankBin, String settlementAccountNumber) {
        public CreatePaymentRequest(long orderCode, long amount, String description, String paymentMethod, Boolean threeDs,
                                    List<Item> items, String returnUrl, String cancelUrl, Instant expiresAt) {
            this(orderCode, amount, description, paymentMethod, threeDs, items, returnUrl, cancelUrl, expiresAt, null, null);
        }
        public CreatePaymentRequest(long orderCode, long amount, String description, String paymentMethod,
                                    List<Item> items, String returnUrl, String cancelUrl, Instant expiresAt) {
            this(orderCode, amount, description, paymentMethod, null, items, returnUrl, cancelUrl, expiresAt, null, null);
        }
    }

    public record Item(@NotBlank String name, @Positive int quantity, @Positive long price) {}

    public record PaymentResponse(String providerPaymentId, String checkoutUrl, String qrCode,
                                  boolean threeDsRequired, boolean threeDsChoiceRequired) {
        public PaymentResponse(String providerPaymentId, String checkoutUrl, String qrCode) {
            this(providerPaymentId, checkoutUrl, qrCode, false, false);
        }
    }

    public record PaymentStatusResponse(String status, long amountPaid, String transactionRef, Instant paidAt,
                                        boolean threeDsRequired, boolean threeDsAuthenticated, String threeDsStatus) {
        public PaymentStatusResponse(String status, long amountPaid, String transactionRef, Instant paidAt) {
            this(status, amountPaid, transactionRef, paidAt, false, false, "NOT_REQUIRED");
        }
    }

    public record RefundRequest(@NotBlank String referenceId, @Positive long amount, @NotBlank String description,
                                String toBin, @NotBlank String toAccountNumber, String providerPaymentId) {}

    public record RefundResponse(String providerRefundId, String status, String failureCode, String failureReason) {}

    public record BalanceResponse(long available) {}
}
