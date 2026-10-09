package com.bankSimulate.infrastructure.bank;

import com.bankSimulate.domain.bank.BankProcessor;
import com.bankSimulate.domain.bank.BankProfile;
import com.bankSimulate.domain.bank.BankRefundResult;

import java.time.Instant;
import java.util.UUID;

public final class ConfiguredBankProcessor implements BankProcessor {
    private final BankProfile profile;

    public ConfiguredBankProcessor(BankProfile profile) {
        this.profile = profile;
    }

    @Override
    public BankProfile profile() {
        return profile;
    }

    @Override
    public BankRefundResult refund(String referenceId, long amount, String payerAccountNumber) {
        return new BankRefundResult("bs_rf_" + UUID.randomUUID(), "SUCCEEDED", null, null, Instant.now());
    }
}
