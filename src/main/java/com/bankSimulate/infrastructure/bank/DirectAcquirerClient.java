package com.bankSimulate.infrastructure.bank;

import com.bankSimulate.domain.cardAuth.CardAuthorizationCommand;
import com.bankSimulate.domain.cardAuth.CardAuthorizationPort;
import com.bankSimulate.domain.cardAuth.CardAuthorizationResult;
import com.bankSimulate.domain.cardAuth.BankFailure;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.mockbank.web.dto.MockBankBDtos;
import com.bankSimulate.infrastructure.security.BankRequestSigner;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientException;

@Component
public class DirectAcquirerClient implements CardAuthorizationPort {

    private final AcquirerEndpoints endpoints;
    private final BankRequestSigner signer;
    private final ObjectMapper json;

    public DirectAcquirerClient(AcquirerEndpoints endpoints, BankRequestSigner signer, ObjectMapper json) {
        this.endpoints = endpoints;
        this.signer = signer;
        this.json = json;
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
        // Ký đúng chuỗi sẽ gửi: serialize một lần, ký và gửi cùng biến raw
        String raw = json.writeValueAsString(request);
        BankRequestSigner.Signed signed = signer.sign(acquirerCode, raw);
        MockBankBDtos.AuthorizeResponse response;
        try {
            response = endpoints.client(acquirerCode).post().uri("/mock-bank/{bank}/payment/authorize", acquirerCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(BankRequestSigner.TIMESTAMP_HEADER, signed.timestamp())
                    .header(BankRequestSigner.SIGNATURE_HEADER, signed.signature())
                    .body(raw).retrieve().body(MockBankBDtos.AuthorizeResponse.class);
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
