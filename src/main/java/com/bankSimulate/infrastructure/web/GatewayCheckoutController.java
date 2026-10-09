package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.QrPaymentService;
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

    public GatewayCheckoutController(QrPaymentService service) {
        this.service = service;
    }

    @GetMapping("/{tradeNo}")
    public ResponseEntity<String> page(@PathVariable String tradeNo) {
        return ResponseEntity.ok().contentType(HTML_UTF8).body(QrCheckoutPage.render(service.checkout(tradeNo)));
    }

    public record GooglePayRequest(String token, String scenario) {}

    @PostMapping("/{tradeNo}/google-pay")
    public QrPaymentService.GooglePayResult googlePay(@PathVariable String tradeNo, @RequestBody GooglePayRequest body) {
        return service.payWithGooglePay(tradeNo, body.token(), body.scenario());
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
