package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.CardPaymentService;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.security.CallbackSigner;
import com.bankSimulate.mockbank.web.dto.MockBankADtos;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public class BankCallbackController {

    private final CardPaymentService service;
    private final CallbackSigner signer;
    private final ObjectMapper json;

    private static final Logger log = LoggerFactory.getLogger(BankCallbackController.class);


    public BankCallbackController(CardPaymentService service, CallbackSigner signer, ObjectMapper json) {
        this.service = service;
        this.signer = signer;
        this.json = json;
    }

    @PostMapping("/gateway-callback/{bank}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notify(@PathVariable String bank, @RequestBody String rawBody,
                      @RequestHeader(value = "X-Bank-Signature", required = false) String signature) {
        if (!signer.matches(rawBody, signature))
            throw new ApiException(401, "INVALID_BANK_SIGNATURE", "Callback signature does not match");

        MockBankADtos.Notification notification = json.readValue(rawBody, MockBankADtos.Notification.class);
        boolean approved = MockBankADtos.RESP_APPROVED.equals(notification.respCode());

        log.info("Bank callback bank={} respCode={} approved={}",bank, notification.txnId(), notification.respCode(), notification.respMsg());
        service.handleBankNotification(bank, notification.txnId(), approved,
                notification.authCode(), notification.respMsg());
    }
}
