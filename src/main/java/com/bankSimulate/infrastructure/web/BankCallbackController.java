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


    // Body chưa verify nên txnId chỉ dùng để tìm giao dịch gắn prefix log, không dùng để xử lý
    private void tagLog(String bank, String rawBody) {
        try {
            String bankRef = json.readTree(rawBody).path("txnId").asText(null);
            if (bankRef != null && !bankRef.isBlank())
                service.tagLogForBankCallback(bank, bankRef);
        } catch (RuntimeException ignored) {
            // body hỏng: để signer.matches từ chối, log không có prefix giao dịch
        }
    }

    public BankCallbackController(CardPaymentService service, CallbackSigner signer, ObjectMapper json) {
        this.service = service;
        this.signer = signer;
        this.json = json;
    }

    @PostMapping("/gateway-callback/{bank}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notify(@PathVariable String bank, @RequestHeader(value = "X-Bank-Timestamp", required =false) String timestamp,  @RequestBody String rawBody,
                      @RequestHeader(value = "X-Bank-Signature", required = false) String signature) {
        tagLog(bank, rawBody);
        if (!signer.matches(bank, timestamp, rawBody, signature))
            throw new ApiException(401, "INVALID_BANK_SIGNATURE", "Callback signature does not match");

        MockBankADtos.Notification notification = json.readValue(rawBody, MockBankADtos.Notification.class);
        boolean approved = MockBankADtos.RESP_APPROVED.equals(notification.respCode());

        log.info("Bank callback bank={} txnId={} respCode={} approved={}",
                bank, notification.txnId(), notification.respCode(), approved);
        service.handleBankNotification(bank, notification.txnId(), approved,
                notification.authCode(), notification.respMsg());
    }
}
