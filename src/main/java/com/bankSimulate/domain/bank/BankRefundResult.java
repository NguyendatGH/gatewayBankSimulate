package com.bankSimulate.domain.bank;

import java.time.Instant;

public record BankRefundResult(
        String providerRefundId,
        String status,
        String failureCode,
        String failureReason,
        Instant processedAt
) {
}