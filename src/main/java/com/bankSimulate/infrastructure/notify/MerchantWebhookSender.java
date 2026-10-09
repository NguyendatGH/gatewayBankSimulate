package com.bankSimulate.infrastructure.notify;

import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MerchantWebhookSender {
    private static final String SIGNATURE_HEADER = "X-Mock-Signature";

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookSender.class);

    private final ObjectMapper json;
    private final RestClient http = RestClient.create();

    public MerchantWebhookSender(ObjectMapper json) {
        this.json = json;
    }

    public void send(String webhookUrl, String merchantSecret, String gwTxnId, String orderCode,
                     String resultCode, String bankRef, String authCode, long amount, String message,
                     java.time.Instant paidAt) {

        try (GatewayLogContext.Scope ignored = GatewayLogContext.withBank(GatewayLogContext.MERCHANT)) {
            GatewayLogContext.setTradeNo(gwTxnId);
            GatewayLogContext.setOrderNo(orderCode);
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.info("Merchant has no webhook url configured; skipping notification");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("eventId", gwTxnId + ":" + resultCode);
            body.put("gwTxnId", gwTxnId);
            body.put("orderCode", orderCode);
            body.put("resultCode", resultCode);

            body.put("success", "02".equals(resultCode));
            body.put("bankRef", bankRef);
            body.put("transactionRef", bankRef);
            body.put("authCode", authCode);
            body.put("amount", amount);
            body.put("paidAt", paidAt == null ? null : paidAt.toString());
            body.put("message", message);

            post(webhookUrl, merchantSecret, body, "resultCode=" + resultCode);
        }
    }

    public void sendQr(String webhookUrl, String merchantSecret, Map<String, Object> body) {
        try (GatewayLogContext.Scope ignored = GatewayLogContext.withBank(GatewayLogContext.MERCHANT)) {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.info("Merchant has no webhook url configured; skipping notification");
                return;
            }
            post(webhookUrl, merchantSecret, body, "success=" + body.get("success"));
        }
    }

    private void post(String webhookUrl, String merchantSecret, Map<String, Object> body, String label) {
        String raw = json.writeValueAsString(body);
        log.info("webhook ->POST {} {}", webhookUrl , label);
        try {
            var response = http.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
                .header(SIGNATURE_HEADER, sign(raw, merchantSecret))
                .body(raw).retrieve().toBodilessEntity();
            log.info("Webhook delivered {} status={} url={}", label, response.getStatusCode().value(), webhookUrl);
        } catch (RuntimeException ex) {
            log.warn("Webhook delivery failed {}: {}", label, ex.toString());
        }
    }

    private static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
