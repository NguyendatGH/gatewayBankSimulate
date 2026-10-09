package com.bankSimulate.domain.bank;

public record BankProfile(String code, String name, String bankBin) {
    public BankProfile {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Bank code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Bank name is required");
    }
}
