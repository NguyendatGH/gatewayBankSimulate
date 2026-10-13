package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.application.GatewayRuntimeService;
import com.bankSimulate.application.RefundPayoutService;
import com.bankSimulate.infrastructure.web.dto.GatewayRuntimeDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayRuntimeController {
    private final GatewayRuntimeAuth auth;
    private final GatewayRuntimeService service;
    private final RefundPayoutService refunds;

    public GatewayRuntimeController(GatewayRuntimeAuth auth, GatewayRuntimeService service, RefundPayoutService refunds) {
        this.auth = auth;
        this.service = service;
        this.refunds = refunds;
    }

    @PostMapping("/payments")
    public GatewayRuntimeDtos.PaymentResponse createPayment(@RequestHeader("X-Merchant-No") String merNo,
                                                            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                            @RequestHeader("X-Merchant-Secret") String secret,
                                                            @Valid @RequestBody GatewayRuntimeDtos.CreatePaymentRequest request) {
        GatewayRuntimeAuth.Access access = terminalId == null || terminalId.isBlank()
                ? auth.authenticateMerchant(merNo, secret) : auth.authenticate(merNo, terminalId, secret);
        return service.createPayment(access, request);
    }

    @GetMapping("/payments/{id}")
    public GatewayRuntimeDtos.PaymentStatusResponse paymentStatus(@RequestHeader("X-Merchant-No") String merNo,
                                                                   @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                                   @RequestHeader("X-Merchant-Secret") String secret,
                                                                   @PathVariable String id) {
        return service.paymentStatus(auth.authenticateMerchant(merNo, secret), id);
    }

    @PostMapping("/payments/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelPayment(@RequestHeader("X-Merchant-No") String merNo, @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                              @RequestHeader("X-Merchant-Secret") String secret, @PathVariable String id) {
        service.cancelPayment(auth.authenticateMerchant(merNo, secret), id);
    }

    @PostMapping("/refunds")
    public GatewayRuntimeDtos.RefundResponse submitRefund(@RequestHeader("X-Merchant-No") String merNo, @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                          @RequestHeader("X-Merchant-Secret") String secret,
                                                          @Valid @RequestBody GatewayRuntimeDtos.RefundRequest request) {
        return refunds.submit(auth.authenticateMerchant(merNo, secret), request);
    }

    @GetMapping("/refunds/{id}")
    public GatewayRuntimeDtos.RefundResponse refundStatus(@RequestHeader("X-Merchant-No") String merNo, @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                          @RequestHeader("X-Merchant-Secret") String secret, @PathVariable String id) {
        return refunds.status(auth.authenticateMerchant(merNo, secret), id);
    }

    @GetMapping("/refunds/by-reference")
    public GatewayRuntimeDtos.RefundResponse refundByReference(@RequestHeader("X-Merchant-No") String merNo, @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                               @RequestHeader("X-Merchant-Secret") String secret, @RequestParam String referenceId) {
        return refunds.byReference(auth.authenticateMerchant(merNo, secret), referenceId);
    }

    @GetMapping("/payout/balance")
    public GatewayRuntimeDtos.BalanceResponse payoutBalance(@RequestHeader("X-Merchant-No") String merNo, @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId,
                                                            @RequestHeader("X-Merchant-Secret") String secret) {
        auth.authenticateMerchant(merNo, secret);
        return new GatewayRuntimeDtos.BalanceResponse(refunds.payoutBalance());
    }
}
