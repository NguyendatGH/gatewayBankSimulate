package com.bankSimulate.googlepay;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint trình duyệt gọi sau khi khách chọn thẻ trong sheet Google Pay. */
@RestController
@RequestMapping("/checkout")
public class GooglePayController {

    public record Request(String token, String scenario) {}

    private final GooglePayService service;

    public GooglePayController(GooglePayService service) {
        this.service = service;
    }

    @PostMapping("/{tradeNo}/google-pay")
    public GooglePayService.Result pay(@PathVariable String tradeNo, @RequestBody Request body) {
        return service.pay(tradeNo, body.token(), body.scenario());
    }
}
