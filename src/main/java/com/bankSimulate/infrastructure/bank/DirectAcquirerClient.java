package com.bankSimulate.infrastructure.bank;

import com.bankSimulate.domain.cardAuth.CardAuthorizationCommand;
import com.bankSimulate.domain.cardAuth.CardAuthorizationPort;
import com.bankSimulate.domain.cardAuth.CardAuthorizationResult;
import com.bankSimulate.domain.cardAuth.BankFailure;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.mockbank.web.dto.MockBankBDtos;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class DirectAcquirerClient implements CardAuthorizationPort {

    private final AcquirerEndpoints endpoints;

    public DirectAcquirerClient(AcquirerEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public boolean threeDs() {
        return false;
    }

    @Override
    public CardAuthorizationResult authorize(String acquirerCode, CardAuthorizationCommand command) {
        MockBankBDtos.AuthorizeRequest request = new MockBankBDtos.AuthorizeRequest(
                command.merchantNo(), command.gwTxnId(), command.amount(), command.currency(),
                command.pan(), command.expiryMonth(), command.expiryYear(), command.cvv(), command.notifyUrl());
        MockBankBDtos.AuthorizeResponse response;
        try {
            response = endpoints.client(acquirerCode).post().uri("/mock-bank/{bank}/payment/authorize", acquirerCode)
                    .body(request).retrieve().body(MockBankBDtos.AuthorizeResponse.class);
        } catch (RestClientException ex) {
            throw BankTransportErrors.translate(acquirerCode, ex);
        }
        if (response == null)
            throw new ApiException(502, BankFailure.EMPTY_RESPONSE, acquirerCode + " returned no body");
        return MockBankBDtos.STATUS_APPROVED.equals(response.status())
                ? CardAuthorizationResult.approved(response.bankTransactionId(), response.bankTransactionId(),
                response.status(), response.message())
                : CardAuthorizationResult.declined(response.bankTransactionId(), response.status(), response.message());
    }
}
