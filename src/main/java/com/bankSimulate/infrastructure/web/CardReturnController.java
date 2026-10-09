package com.bankSimulate.infrastructure.web;

import com.bankSimulate.domain.gateway.GatewayTransaction;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.persistence.GatewayTransactionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class CardReturnController {

    private final GatewayTransactionRepository transactions;

    public CardReturnController(GatewayTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/gateway-return/{gwTxnId}")
    public ResponseEntity<Void> back(@PathVariable String gwTxnId) {
        GatewayTransaction txn = transactions.findByGwTxnId(gwTxnId)
                .orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "Transaction not found"));

        String target = UriComponentsBuilder.fromUriString(txn.getMerchantReturnUrl())
                .queryParam("gwTxnId", txn.getGwTxnId())
                .queryParam("resultCode", txn.getResultCode() == null ? "" : txn.getResultCode())
                .build().toUriString();

        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, target).build();
    }
}
