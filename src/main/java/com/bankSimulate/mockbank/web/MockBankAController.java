package com.bankSimulate.mockbank.web;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.security.CallbackSigner;
import com.bankSimulate.mockbank.MockBankAuthorizeInput;
import com.bankSimulate.mockbank.web.dto.MockBankADtos;
import com.bankSimulate.mockbank.MockBankDecision;
import com.bankSimulate.mockbank.MockBankSessionStore;
import com.bankSimulate.mockbank.ThreeDsMockBank;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/mock-bank/{bank}")
public class MockBankAController {

    private static final Logger log = LoggerFactory.getLogger(MockBankAController.class);

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final ThreeDsMockBank issuer;
    private final MockBankSessionStore sessions;
    private final CallbackSigner signer;
    private final ObjectMapper json;
    private final RestClient http = RestClient.create();
    private final String publicBaseUrl;

    public MockBankAController(ThreeDsMockBank issuer, MockBankSessionStore sessions, CallbackSigner signer,
                               ObjectMapper json,
                               @org.springframework.beans.factory.annotation.Value("${gateway.public-base-url:http://localhost:8090}") String publicBaseUrl) {
        this.issuer = issuer;
        this.sessions = sessions;
        this.signer = signer;
        this.json = json;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    @PostMapping("/v1/authorize")
    public MockBankADtos.AuthorizeResponse authorize(@PathVariable String bank,
                                                     @Valid @RequestBody MockBankADtos.AuthorizeRequest request) {
        tagLog(bank);
        GatewayLogContext.setMerchantNo(request.merchantId());
        GatewayLogContext.setTradeNo(request.txnRef());
        MockBankAuthorizeInput input = new MockBankAuthorizeInput(request.txnRef(), parseAmount(request.txnAmount()),
                "704".equals(request.ccy()) ? "VND" : request.ccy(), request.card().number(),
                expiryMonth(request.card().expiry()), expiryYear(request.card().expiry()), request.card().cvv(),
                preference(request.authPreference()), request.notifyUrl(), request.termUrl());

        MockBankDecision decision = issuer.authorize(input);
        return switch (decision.kind()) {
            case APPROVE -> new MockBankADtos.AuthorizeResponse(MockBankADtos.RESP_APPROVED, decision.message(),
                    decision.bankRef(), null);
            case DECLINE -> new MockBankADtos.AuthorizeResponse(MockBankADtos.RESP_DECLINED, decision.message(),
                    decision.bankRef(), null);
            case CHALLENGE -> new MockBankADtos.AuthorizeResponse(MockBankADtos.RESP_AUTH_REQUIRED, decision.message(),
                    decision.bankRef(), publicBaseUrl + "/mock-bank/" + bank + "/auth/" + decision.bankRef());
        };
    }

    @GetMapping(value = "/auth/{bankRef}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String bank, @PathVariable String bankRef) {
        tagLog(bank);
        MockBankSessionStore.Session session = session(bankRef);
        return otpPage(bank, bankRef, session, null);
    }

    @PostMapping(value = "/auth/{bankRef}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> submit(@PathVariable String bank, @PathVariable String bankRef, @RequestParam String otp) {
        tagLog(bank);
        MockBankSessionStore.Session session = session(bankRef);
        MockBankDecision decision;
        try {
            decision = issuer.authenticate(bankRef, otp);
        } catch (ApiException retryable) {

            return ResponseEntity.ok().contentType(HTML_UTF8)
                    .body(otpPage(bank, bankRef, session, retryable.getMessage()));
        }

        notifyGateway(session, decision);
        sessions.remove(bankRef);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, session.termUrl()).build();
    }

    @PostMapping("/auth/{bankRef}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String bank, @PathVariable String bankRef) {
        tagLog(bank);
        MockBankSessionStore.Session session = session(bankRef);
        session.close();
        notifyGateway(session, MockBankDecision.decline(bankRef, "Cardholder cancelled at bank page"));
        sessions.remove(bankRef);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, session.termUrl()).build();
    }

    private void notifyGateway(MockBankSessionStore.Session session, MockBankDecision decision) {
        MockBankADtos.Notification body = new MockBankADtos.Notification(decision.bankRef(),
                decision.kind() == MockBankDecision.Kind.APPROVE ? MockBankADtos.RESP_APPROVED : MockBankADtos.RESP_DECLINED,
                decision.message(), decision.authCode());
        String raw = json.writeValueAsString(body);
        try {
            http.post().uri(session.notifyUrl()).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Bank-Signature", signer.sign(raw))
                    .body(raw).retrieve().toBodilessEntity();
            log.info("Notified gateway ref={} respCode={}", decision.bankRef(), body.respCode());
        } catch (RuntimeException ex) {

            log.warn("Could not notify gateway ref={}: {}", decision.bankRef(), ex.toString());
        }
    }

    private static void tagLog(String bank) {
        GatewayLogContext.setBank(bank);
    }

    private MockBankSessionStore.Session session(String bankRef) {
        MockBankSessionStore.Session session = sessions.find(bankRef)
                .orElseThrow(() -> new ApiException(404, "AUTH_SESSION_NOT_FOUND", "Authentication session not found"));
        GatewayLogContext.setTradeNo(session.reference());
        return session;
    }

    private static MockBankAuthorizeInput.AuthPreference preference(String raw) {
        if (MockBankADtos.PREF_CHALLENGE.equals(raw)) return MockBankAuthorizeInput.AuthPreference.CHALLENGE;
        if (MockBankADtos.PREF_SKIP.equals(raw)) return MockBankAuthorizeInput.AuthPreference.SKIP;
        return MockBankAuthorizeInput.AuthPreference.NONE;
    }

    private static long parseAmount(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "INVALID_AMOUNT", "txnAmount must be a whole number");
        }
    }

    private static int expiryMonth(String expiry) {
        return Integer.parseInt(split(expiry)[0]);
    }

    private static int expiryYear(String expiry) {
        int yy = Integer.parseInt(split(expiry)[1]);
        return yy < 100 ? 2000 + yy : yy;
    }

    private static String[] split(String expiry) {
        String[] parts = expiry == null ? new String[0] : expiry.split("/");
        if (parts.length != 2) throw new ApiException(400, "INVALID_EXPIRY", "expiry must look like MM/YY");
        return parts;
    }

    private String otpPage(String bank, String bankRef, MockBankSessionStore.Session session, String error) {
        String safeRef = esc(bankRef);
        return """
                <!doctype html><html lang="vi"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Xác thực giao dịch · Mock Bank A</title><style>
                :root{font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;color:#10243a}
                *{box-sizing:border-box}body{margin:0;min-height:100vh;background:#eef3f8;display:grid;place-items:center;padding:24px}
                .card{background:#fff;border:1px solid #d3dfea;border-radius:16px;max-width:420px;width:100%;overflow:hidden}
                .bar{background:#0b4f7a;color:#fff;padding:16px 22px;display:flex;align-items:center;gap:10px;font-weight:700}
                .bar span{font-size:12px;font-weight:500;opacity:.85;margin-left:auto}
                .body{padding:22px}h1{font-size:17px;margin:0 0 14px}
                .row{display:flex;justify-content:space-between;font-size:14px;padding:9px 0;border-bottom:1px solid #eef2f6}
                .row b{font-weight:650}label{display:block;font-size:13px;font-weight:650;margin:18px 0 7px}
                input{width:100%;height:46px;border:1px solid #bccbda;border-radius:9px;padding:0 13px;font:inherit;letter-spacing:.18em}
                button{width:100%;height:48px;margin-top:14px;border:0;border-radius:9px;background:#0b4f7a;color:#fff;font:inherit;font-weight:650;cursor:pointer}
                .ghost{background:#eef2f6;color:#41566b;margin-top:8px}
                .err{color:#b3261e;font-size:13px;margin:10px 0 0}
                .hint{color:#64788c;font-size:12px;line-height:1.55;margin:16px 0 0}
                </style></head><body><div class="card">
                <div class="bar">__BANK__<span>Trang của ngân hàng</span></div>
                <div class="body">
                  <h1>Xác thực chủ thẻ</h1>
                  <div class="row"><span>Thẻ</span><b>•••• __LAST4__</b></div>
                  <div class="row"><span>Số tiền</span><b>__AMOUNT__ __CCY__</b></div>
                  <div class="row"><span>Mã giao dịch</span><b>__REF__</b></div>
                  <form method="post" action="/mock-bank/__BANK_PATH__/auth/__REF__">
                    <label for="otp">Mã OTP ngân hàng gửi cho bạn</label>
                    <input id="otp" name="otp" inputmode="numeric" autocomplete="one-time-code"
                           pattern="[0-9]{6}" maxlength="6" required placeholder="______">
                    __ERROR__
                    <button type="submit">Xác thực</button>
                  </form>
                  <form method="post" action="/mock-bank/__BANK_PATH__/auth/__REF__/cancel">
                    <button class="ghost" type="submit">Huỷ giao dịch</button>
                  </form>
                  <p class="hint">Sandbox: OTP đúng là <b>123456</b>. Sai 3 lần thì giao dịch bị từ chối.
                  Gateway và merchant không nhìn thấy mã này.</p>
                </div></div></body></html>
                """
                .replace("__LAST4__", esc(session.panLast4()))
                .replace("__AMOUNT__", java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(session.amount()))
                .replace("__CCY__", esc(session.currency()))
                .replace("__REF__", safeRef)
                .replace("__BANK_PATH__", esc(bank))
                .replace("__BANK__", esc(bank.toUpperCase(java.util.Locale.ROOT)))
                .replace("__ERROR__", error == null ? "" : "<p class=\"err\">" + esc(error) + "</p>");
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
