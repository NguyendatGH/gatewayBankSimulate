package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.GuardCheckout;
import com.bankSimulate.application.QrPaymentService;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.googlepay.GooglePayCheckoutPage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/checkout")
public class GatewayCheckoutController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final QrPaymentService service;
    private final GuardCheckout guard;
    private final GooglePayCheckoutPage googlePayPage;

    public GatewayCheckoutController(QrPaymentService service, GuardCheckout guard, GooglePayCheckoutPage googlePayPage) {
        this.service = service;
        this.guard = guard;
        this.googlePayPage = googlePayPage;
    }

    @GetMapping("/{tradeNo}")
    public ResponseEntity<String> page(@PathVariable String tradeNo) {
        QrPaymentService.CheckoutView view = service.checkout(tradeNo);
        String html = googlePayPage.supports(view) ? googlePayPage.render(view) : QrCheckoutPage.render(view);
        return ResponseEntity.ok().contentType(HTML_UTF8).body(html);
    }

    @PostMapping("/{tradeNo}/qr")
    public QrPaymentService.QrRefresh refreshQr(@PathVariable String tradeNo) {
        return service.refreshQr(tradeNo);
    }


    @PostMapping("/{tradeNo}/{action:succeed|fail|expire}")
    public ResponseEntity<Void> act(@PathVariable String tradeNo, @PathVariable String action, @RequestParam(name = "qr", required = false) String qrId) {
         QrPaymentService.Action a = switch (action) {
            case "succeed" -> QrPaymentService.Action.SUCCEED;
            case "fail" -> QrPaymentService.Action.FAIL;
            default -> QrPaymentService.Action.EXPIRE;
        };
        String redirect ;
        try {
            redirect = service.complete(tradeNo, a, qrId);
        } catch (ApiException e) {
            if (!"QR_EXPIRED".equals(e.getCode())) throw e; 
            return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/checkout/" + tradeNo).build();
        }
        guard.close(tradeNo);     
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, redirect).build();
    }
}
