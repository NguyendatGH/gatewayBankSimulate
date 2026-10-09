package com.bankSimulate.infrastructure.web;

import com.bankSimulate.application.CardPaymentService;
import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.infrastructure.persistence.AcquirerRepository;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.GateWayTransactionStatus;
import com.bankSimulate.domain.gateway.GatewayTransaction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

@RestController
@RequestMapping("/card-checkout")
public class CardCheckoutController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final CardPaymentService service;
    private final AcquirerRepository acquirers;

    public CardCheckoutController(CardPaymentService service, AcquirerRepository acquirers) {
        this.service = service;
        this.acquirers = acquirers;
    }


    private String testCardHint(String bankCode) {
        boolean threeDs = acquirers.findByCode(bankCode).map(Acquirer::isThreeDsSupported).orElse(false);
        return threeDs
                ? "Thẻ đuôi <b>1000</b> luôn hỏi OTP (<b>123456</b>), thẻ đuôi <b>2000</b> được duyệt không cần xác thực, đuôi khác bị từ chối."
                : "Ngân hàng này không có 3DS: chỉ thẻ đuôi <b>1000</b> được duyệt (không hỏi OTP), đuôi khác bị từ chối.";
    }

    @GetMapping(value = "/{gwTxnId}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String gwTxnId) {
        return render(service.require(gwTxnId), null);
    }

    @PostMapping(value = "/{gwTxnId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> submit(@PathVariable String gwTxnId,
                                         @RequestParam String pan,
                                         @RequestParam int expiryMonth,
                                         @RequestParam int expiryYear,
                                         @RequestParam String cvv) {
        String clean = pan == null ? "" : pan.replaceAll("\\s", "");
        if (!clean.matches("[0-9]{13,19}") || cvv == null || !cvv.matches("[0-9]{3,4}")
                || expiryMonth < 1 || expiryMonth > 12) {
            return ResponseEntity.ok().contentType(HTML_UTF8)
                    .body(render(service.require(gwTxnId), "Thông tin thẻ chưa hợp lệ. Kiểm tra lại số thẻ, hạn dùng và CVV."));
        }
        String target;
        try {
            target = service.submitCard(gwTxnId, clean, expiryMonth, expiryYear, cvv);
        } catch (ApiException rejected) {
            return ResponseEntity.ok().contentType(HTML_UTF8)
                    .body(render(service.require(gwTxnId), rejected.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, target).build();
    }

    private String render(GatewayTransaction txn, String error) {
        if (txn.getStatus() != GateWayTransactionStatus.CREATED) {
            return """
                    <!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Giao dịch đã xử lý</title>
                    <style>body{font-family:ui-sans-serif,system-ui,sans-serif;display:grid;place-items:center;
                    min-height:100vh;margin:0;background:#f4f6fa;color:#1d2433}
                    .box{background:#fff;border:1px solid #dde3ec;border-radius:14px;padding:28px;max-width:420px}
                    a{color:#4f46c7}</style></head><body><div class="box">
                    <h1 style="font-size:17px;margin:0 0 10px">Giao dịch này đã được xử lý</h1>
                    <p style="margin:0;color:#5c6b80;font-size:14px">Trạng thái: <b>__STATUS__</b>.
                    <a href="__RETURN__">Quay lại cửa hàng</a></p></div></body></html>
                    """
                    .replace("__STATUS__", txn.getStatus().name())
                    .replace("__RETURN__", esc(txn.getMerchantReturnUrl()));
        }
        return """
                <!doctype html><html lang="vi"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Thanh toán thẻ · Encore Gateway</title><style>
                :root{font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;color:#17152a}
                *{box-sizing:border-box}body{margin:0;min-height:100vh;background:#f5f4fa;display:grid;place-items:center;padding:24px}
                .card{background:#fff;border:1px solid #e4e1ee;border-radius:18px;max-width:440px;width:100%;overflow:hidden}
                .bar{padding:16px 22px;border-bottom:1px solid #eeecf4;display:flex;align-items:center;gap:10px;font-weight:750}
                .bar .mark{width:26px;height:26px;border-radius:8px;background:#5e56d6;color:#fff;display:grid;place-items:center;font-size:14px}
                .bar span{margin-left:auto;font-size:11px;font-weight:600;color:#2c7a52}
                .body{padding:22px}
                .row{display:flex;justify-content:space-between;font-size:14px;padding:9px 0;border-bottom:1px solid #f1eff7}
                .row b{font-weight:650}
                label{display:block;font-size:12px;font-weight:650;margin:16px 0 7px}
                input{width:100%;height:46px;border:1px solid #cbc8dc;border-radius:10px;padding:0 13px;font:inherit}
                .cols{display:grid;grid-template-columns:1fr 1fr .9fr;gap:10px}
                button{width:100%;height:48px;margin-top:18px;border:0;border-radius:10px;background:#5e56d6;color:#fff;font:inherit;font-weight:650;cursor:pointer}
                .err{color:#b3261e;font-size:13px;margin:12px 0 0}
                .hint{color:#6c6881;font-size:12px;line-height:1.6;margin:16px 0 0}
                .hint b{color:#45415c}
                </style></head><body><div class="card">
                <div class="bar"><span class="mark">e</span>Encore Gateway<span>▣ SANDBOX</span></div>
                <div class="body">
                  <div class="row"><span>Mã đơn</span><b>__ORDER__</b></div>
                  <div class="row"><span>Số tiền</span><b>__AMOUNT__ __CCY__</b></div>
                  <div class="row"><span>Ngân hàng xử lý</span><b>__BANK__</b></div>
                  <form method="post" action="/card-checkout/__ID__">
                    <label for="pan">Số thẻ</label>
                    <input id="pan" name="pan" inputmode="numeric" autocomplete="cc-number" required
                           placeholder="4000 0000 0000 1000">
                    <div class="cols">
                      <div><label for="expiryMonth">Tháng</label>
                        <input id="expiryMonth" name="expiryMonth" type="number" min="1" max="12" required placeholder="12"></div>
                      <div><label for="expiryYear">Năm</label>
                        <input id="expiryYear" name="expiryYear" type="number" min="2026" max="2099" required placeholder="2030"></div>
                      <div><label for="cvv">CVV</label>
                        <input id="cvv" name="cvv" inputmode="numeric" autocomplete="off" required placeholder="123"></div>
                    </div>
                    __ERROR__
                    <button type="submit">Thanh toán</button>
                  </form>
                  <p class="hint">Thẻ thử: <b>4000 0000 0000 1000</b> · 12/2030 · CVV 123.
                  __CARD_HINT__
                  Số thẻ không được lưu — chỉ giữ 6 số đầu và 4 số cuối.</p>
                </div></div></body></html>
                """
                .replace("__ORDER__", esc(txn.getOrderCode()))
                .replace("__AMOUNT__", NumberFormat.getIntegerInstance(Locale.US).format(txn.getAmount()))
                .replace("__CCY__", esc(txn.getCurrency()))
                .replace("__BANK__", esc(txn.getBankCode()))
                .replace("__CARD_HINT__", testCardHint(txn.getBankCode()))
                .replace("__ID__", esc(txn.getGwTxnId()))
                .replace("__ERROR__", error == null ? "" : "<p class=\"err\">" + esc(error) + "</p>");
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
