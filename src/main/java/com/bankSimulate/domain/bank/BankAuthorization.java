package com.bankSimulate.domain.bank;

import java.time.Instant;

public record BankAuthorization(boolean approved, String transactionRef, Instant processedAt, String failureCode,
                                String failureReason) {
}
