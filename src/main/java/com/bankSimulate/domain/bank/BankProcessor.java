package com.bankSimulate.domain.bank;

public interface BankProcessor {
    BankProfile profile();

    BankRefundResult refund(String referenceId, long amount, String payerAccountNumber);
}
