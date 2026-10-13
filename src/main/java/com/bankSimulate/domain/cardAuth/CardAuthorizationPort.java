package com.bankSimulate.domain.cardAuth;

public interface CardAuthorizationPort {
    boolean threeDs();

    CardAuthorizationResult authorize(String acquirerCode, CardAuthorizationCommand command);
}
