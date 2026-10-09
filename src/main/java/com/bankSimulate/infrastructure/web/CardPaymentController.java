package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.CardPaymentService;
import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.infrastructure.web.dto.CardPaymentDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gateway/card-payments")
public class CardPaymentController {

    private final GatewayRuntimeAuth auth;
    private final CardPaymentService service;

    public CardPaymentController(GatewayRuntimeAuth auth, CardPaymentService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    public CardPaymentDtos.CreateResponse create(@RequestHeader("X-Merchant-No") String merNo,
                                                 @RequestHeader("X-Terminal-Id") String terminalId,
                                                 @RequestHeader("X-Merchant-Secret") String secret,
                                                 @Valid @RequestBody CardPaymentDtos.CreateRequest request) {
        return service.create(auth.authenticate(merNo, terminalId, secret), request);
    }

    @PostMapping("/{gwTxnId}/cancel")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader("X-Merchant-No") String merNo,
                       @RequestHeader("X-Terminal-Id") String terminalId,
                       @RequestHeader("X-Merchant-Secret") String secret,
                       @PathVariable String gwTxnId) {
        service.cancel(auth.authenticate(merNo, terminalId, secret), gwTxnId);
    }

    @GetMapping("/{gwTxnId}")
    public CardPaymentDtos.StatusResponse status(@RequestHeader("X-Merchant-No") String merNo,
                                                 @RequestHeader("X-Terminal-Id") String terminalId,
                                                 @RequestHeader("X-Merchant-Secret") String secret,
                                                 @PathVariable String gwTxnId) {
        return service.status(auth.authenticate(merNo, terminalId, secret), gwTxnId);
    }
}
