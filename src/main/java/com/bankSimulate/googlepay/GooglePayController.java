package com.bankSimulate.googlepay;

import com.bankSimulate.application.GuardCheckout;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class GooglePayController {

    public record Request(String token, String scenario) {}

    private final GooglePayService service;
    private final GuardCheckout guard;

    public GooglePayController(GooglePayService service, GuardCheckout guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping("/{tradeNo}/google-pay")
    public GooglePayService.Result pay(@PathVariable String tradeNo, @RequestBody Request body) {
        GooglePayService.Result result = service.pay(tradeNo, body.token(), body.scenario());
        // Google Pay trả redirectUrl trong JSON rồi JS tự chuyển trang, nên đóng link ở đây khi đã có kết quả cuối.
        // PENDING (bank timeout) thì user còn thử lại được, chưa đóng.
        if (!"PENDING".equals(result.status()))
            guard.close(tradeNo);
        return result;
    }
}
