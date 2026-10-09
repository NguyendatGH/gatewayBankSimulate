package com.bankSimulate.infrastructure.bank;

import com.bankSimulate.domain.cardAuth.CardAuthorizationCommand;
import com.bankSimulate.domain.cardAuth.CardAuthorizationPort;
import com.bankSimulate.domain.cardAuth.CardAuthorizationResult;
import com.bankSimulate.domain.cardAuth.BankFailure;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.ThreeDsPreference;
import com.bankSimulate.mockbank.web.dto.MockBankADtos;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class ThreeDsAcquirerClient implements CardAuthorizationPort {

    private final AcquirerEndpoints endpoints;

    public ThreeDsAcquirerClient(AcquirerEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public boolean threeDs() {
        return true;
    }

    @Override
    public CardAuthorizationResult authorize(String acquirerCode, CardAuthorizationCommand command) {
        MockBankADtos.AuthorizeRequest request = new MockBankADtos.AuthorizeRequest(
                command.merchantNo(), command.gwTxnId(), Long.toString(command.amount()),
                "VND".equals(command.currency()) ? "704" : command.currency(),
                new MockBankADtos.Card(command.pan(), expiry(command), command.cvv()),
                preference(command.threeDsPreference()), command.notifyUrl(), command.termUrl());
        MockBankADtos.AuthorizeResponse response;
        try {
            response = endpoints.client(acquirerCode).post().uri("/mock-bank/{bank}/v1/authorize", acquirerCode)
                    .body(request).retrieve().body(MockBankADtos.AuthorizeResponse.class);
        } catch (RestClientException ex) {
            throw BankTransportErrors.translate(acquirerCode, ex);
        }
        if (response == null)
            throw new ApiException(502, BankFailure.EMPTY_RESPONSE, acquirerCode + " returned no body");
        return switch (response.respCode()) {
            case MockBankADtos.RESP_APPROVED -> CardAuthorizationResult.approved(response.txnId(),
                    response.txnId(), response.respCode(), response.respMsg());
            case MockBankADtos.RESP_AUTH_REQUIRED -> CardAuthorizationResult.redirect(response.txnId(),
                    response.authUrl(), response.respCode(), response.respMsg());
            default -> CardAuthorizationResult.declined(response.txnId(), response.respCode(), response.respMsg());
        };
    }

    private static String preference(ThreeDsPreference preference) {
        if (preference == null) return null;
        return switch (preference) {
            case REQUEST_CHALLENGE -> MockBankADtos.PREF_CHALLENGE;
            case REQUEST_NO_CHALLENGE -> MockBankADtos.PREF_SKIP;
            case NO_PREFERENCE -> null;
        };
    }

    private static String expiry(CardAuthorizationCommand command) {
        return String.format("%02d/%02d", command.expiryMonth(), command.expiryYear() % 100);
    }
}
