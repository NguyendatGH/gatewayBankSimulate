package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.QrPaymentService;
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
    private final GooglePayCheckoutPage googlePayPage;

    public GatewayCheckoutController(QrPaymentService service, GooglePayCheckoutPage googlePayPage) {
        this.service = service;
        this.googlePayPage = googlePayPage;
    }

    @GetMapping("/{tradeNo}")
    public ResponseEntity<String> page(@PathVariable String tradeNo) {
        QrPaymentService.CheckoutView view = service.checkout(tradeNo);
        String html = googlePayPage.supports(view) ? googlePayPage.render(view) : QrCheckoutPage.render(view);
        return ResponseEntity.ok().contentType(HTML_UTF8).body(html);
    }

    @PostMapping("/{tradeNo}/{action:succeed|fail|expire}")
    public ResponseEntity<Void> act(@PathVariable String tradeNo, @PathVariable String action) {
        String redirect = service.complete(tradeNo, switch (action) {
            case "succeed" -> QrPaymentService.Action.SUCCEED;
            case "fail" -> QrPaymentService.Action.FAIL;
            default -> QrPaymentService.Action.EXPIRE;
        });
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, redirect).build();
    }
}
