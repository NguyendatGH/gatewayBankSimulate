package com.bankSimulate.mockbank;

public interface MockBank {

    MockBankDecision authorize(MockBankAuthorizeInput input);

    default MockBankDecision authenticate(String bankRef, String otp) {
        throw new UnsupportedOperationException("This bank does not challenge cardholders");
    }
}
