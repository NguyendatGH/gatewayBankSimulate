package com.bankSimulate.application;

import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.notify.MerchantWebhookSender;
import com.bankSimulate.infrastructure.persistence.AcquirerMerchantConfigRepository;
import com.bankSimulate.infrastructure.persistence.GatewayTransactionRepository;
import com.bankSimulate.infrastructure.web.dto.GatewayRuntimeDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QrPaymentService {

    private static final Logger log = LoggerFactory.getLogger(QrPaymentService.class);

    private static final String SANDBOX_PAYER_BANK_BIN = "970436";
    private static final String SANDBOX_PAYER_ACCOUNT = "0123456789012";
    private static final long DEFAULT_TTL_SECONDS = 900;

    public enum Action { SUCCEED, FAIL, EXPIRE }

    private final GatewayRuntimeAuth auth;
    private final TerminalRoutes terminalRoutes;
    private final AcquirerMerchantConfigRepository acquirerConfigs;
    private final GatewayTransactionRepository transactions;
    private final GatewayMoneyService money;
    private final MerchantWebhookSender webhooks;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String publicBaseUrl;

    public QrPaymentService(GatewayRuntimeAuth auth, TerminalRoutes terminalRoutes,
                            AcquirerMerchantConfigRepository acquirerConfigs,
                            GatewayTransactionRepository transactions, GatewayMoneyService money,
                            MerchantWebhookSender webhooks, JdbcTemplate jdbc, TransactionTemplate tx,
                            @Value("${gateway.public-base-url:http://localhost:8090}") String publicBaseUrl) {
        this.auth = auth;
        this.terminalRoutes = terminalRoutes;
        this.acquirerConfigs = acquirerConfigs;
        this.transactions = transactions;
        this.money = money;
        this.webhooks = webhooks;
        this.jdbc = jdbc;
        this.tx = tx;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public GatewayRuntimeDtos.PaymentResponse create(GatewayRuntimeAuth.Access access,
                                                     GatewayRuntimeDtos.CreatePaymentRequest request, PaymentMethod method) {
        String orderCode = Long.toString(request.orderCode());

        QrRow existing = byOrder(access, orderCode);
        if (existing != null) {
            tagLog(existing);
            log.info("Duplicate orderCode, returning existing payment status={}", existing.status());
            return response(existing);
        }
        if (Boolean.TRUE.equals(request.threeDs()))
            throw new ApiException(409, "THREEDS_CARD_ONLY", "3DS just available for CARD");
        String acquirer = resolveRoute(access, method);
        validateItems(request);
        validateSettlement(request);

        String tradeNo = transactions.nextTradeNo();
        Instant expiresAt = request.expiresAt() == null ? Instant.now().plusSeconds(DEFAULT_TTL_SECONDS) : request.expiresAt();
        try {
            tx.executeWithoutResult(s -> {
                money.register(tradeNo, access.merchant().getId(), access.terminal().getId(), orderCode, request.amount(),
                        access.terminal().getCurrency(), method.name(), acquirer, false, request.returnUrl(),
                        request.cancelUrl(), expiresAt, access.merchant().getWebhookUrl(), request.settlementBankBin(),
                        request.settlementAccountNumber());
                money.updateThreeDs(tradeNo, "NOT_REQUIRED");
            });
        } catch (DuplicateKeyException raced) {

            return response(byOrder(access, orderCode));
        }
        QrRow created = row(tradeNo, false);
        tagLog(created);
        log.info("Payment created: paymentMethod={} acquirer={} amount={}", method, acquirer, request.amount());
        return response(created);
    }

    public GatewayRuntimeDtos.PaymentStatusResponse status(GatewayRuntimeAuth.Access access, String tradeNo) {
        expireIfDue(tradeNo);
        QrRow p = require(tradeNo);
        requireOwner(access, p);
        tagLog(p);
        return new GatewayRuntimeDtos.PaymentStatusResponse(p.status(), p.amountPaid(), p.transactionRef(), p.paidAt(),
                false, false, p.threeDsResult() == null ? "NOT_REQUIRED" : p.threeDsResult());
    }

    public void cancel(GatewayRuntimeAuth.Access access, String tradeNo) {
        requireOwner(access, require(tradeNo));
        tx.executeWithoutResult(s -> {
            QrRow p = row(tradeNo, true);
            tagLog(p);
            if (money.markFinal(tradeNo, "CANCELLED")) log.info("Payment cancelled by merchant");
        });
    }

    public CheckoutView checkout(String tradeNo) {
        expireIfDue(tradeNo);
        QrRow p = require(tradeNo);
        tagLog(p);
        boolean pending = "PENDING".equals(p.status());
        log.info(pending ? "Checkout page opened" : "Checkout page reopened, payment already {}", p.status());
        return new CheckoutView(p.tradeNo(), p.merchantName(), p.method(), p.orderCode(), p.amount(), p.status(), pending,
                "PAID".equals(p.status()) ? p.returnUrl() : p.cancelUrl(), qrPayload(p));
    }

    /**
     * Trạng thái hiện tại của một giao dịch cho các luồng thanh toán riêng của từng phương thức (ví dụ Google Pay)
     * cần kiểm tra trước khi chốt. Hết hạn thì chốt EXPIRED trước, nên trạng thái trả về là trạng thái thật.
     */
    public PaymentState state(String tradeNo) {
        expireIfDue(tradeNo);
        QrRow p = require(tradeNo);
        tagLog(p);
        return new PaymentState(p.method(), p.status(), p.returnUrl(), p.cancelUrl());
    }

    public String complete(String tradeNo, Action action) {
        Outcome outcome = tx.execute(s -> {
            QrRow p = row(tradeNo, true);
            if (p == null) throw notFound();
            tagLog(p);
            if ("PAID".equals(p.status())) {
                log.info("Payment already PAID; duplicate confirmation ignored, no new capture amount={}", p.amount());
                return new Outcome(p.returnUrl(), null);
            }
            if (!"PENDING".equals(p.status())) return new Outcome(p.cancelUrl(), null);
            if (action == Action.EXPIRE || p.isExpired(Instant.now())) {
                money.markFinal(tradeNo, "EXPIRED");
                return new Outcome(p.cancelUrl(), failed(p, "EXPIRED"));
            }
            if (action == Action.FAIL) {
                money.markFinal(tradeNo, "CANCELLED");
                return new Outcome(p.cancelUrl(), failed(p, "CANCELLED"));
            }

            String ref = "BS" + System.currentTimeMillis() + "_" + (p.acquirerCode() == null ? "SANDBOX" : p.acquirerCode());
            Instant paidAt = Instant.now();
            if (money.capture(tradeNo, ref, paidAt)) return new Outcome(p.returnUrl(), paid(p, ref, paidAt));
            return new Outcome(p.cancelUrl(), failed(p, "DECLINED"));
        });
        deliver(outcome.webhook());
        log.info("Checkout result action={} -> {}", action, require(tradeNo).status());
        return outcome.redirect();
    }

    public int expireStale() {
        List<String> due = jdbc.queryForList("""
                SELECT provider_payment_id FROM payment_transactions
                WHERE status = 'PENDING' AND payment_method <> 'CARD' AND expires_at <= now()
                ORDER BY expires_at LIMIT 50
                """, String.class);
        int expired = 0;
        for (String tradeNo : due) {

            try (GatewayLogContext.Scope ignored = GatewayLogContext.withBank(GatewayLogContext.GATEWAY)) {
                if (expireIfDue(tradeNo)) expired++;
            }
        }
        return expired;
    }

    private boolean expireIfDue(String tradeNo) {
        Webhook webhook = tx.execute(s -> {
            QrRow p = row(tradeNo, true);
            if (p == null || !"PENDING".equals(p.status()) || !p.isExpired(Instant.now())) return null;
            tagLog(p);
            if (!money.markFinal(tradeNo, "EXPIRED")) return null;
            log.info("Payment expired, customer did not confirm before {}", p.expiresAt());
            return failed(p, "EXPIRED");
        });
        deliver(webhook);
        return webhook != null;
    }

    private String resolveRoute(GatewayRuntimeAuth.Access access, PaymentMethod method) {
        List<Acquirer> candidates = terminalRoutes.plan(access.terminal(), method).acquirers();
        if (candidates.isEmpty())
            throw new ApiException(409, "ROUTING_NOT_CONFIGURED", "No route configured for " + method);
        for (Acquirer acquirer : candidates) {
            if (!acquirer.accepts(method)
                    || !acquirer.meetsThreeDs(method, access.terminal().getThreeDsPolicy())) continue;
            if (acquirerConfigs.existsByAcquirerIdAndMerchantIdAndStatus(acquirer.getId(), access.merchant().getId(), Status.ACTIVE))
                return acquirer.getCode();
        }
        throw new ApiException(409, "ACQUIRER_NOT_CONFIGURED", "No active merchant acquirer configuration for " + method);
    }

    private static void validateItems(GatewayRuntimeDtos.CreatePaymentRequest request) {
        long total = 0;
        try {
            for (GatewayRuntimeDtos.Item item : request.items())
                total = Math.addExact(total, Math.multiplyExact((long) item.quantity(), item.price()));
        } catch (ArithmeticException overflow) {
            throw new ApiException(400, "ITEM_TOTAL_OVERFLOW", "Item total exceeds supported amount");
        }
        if (total != request.amount())
            throw new ApiException(400, "PAYMENT_AMOUNT_MISMATCH", "amount must equal sum(quantity * price)");
    }

    private static void validateSettlement(GatewayRuntimeDtos.CreatePaymentRequest request) {
        boolean hasBin = request.settlementBankBin() != null && !request.settlementBankBin().isBlank();
        boolean hasAccount = request.settlementAccountNumber() != null && !request.settlementAccountNumber().isBlank();
        if (hasBin != hasAccount || (hasBin && (!request.settlementBankBin().matches("[0-9]{6}")
                || !request.settlementAccountNumber().matches("[0-9]{6,30}"))))
            throw new ApiException(400, "INVALID_SETTLEMENT_ACCOUNT", "Settlement bank BIN and account number must be valid together");
    }

    private GatewayRuntimeDtos.PaymentResponse response(QrRow p) {
        return new GatewayRuntimeDtos.PaymentResponse(p.tradeNo(), publicBaseUrl + "/checkout/" + p.tradeNo(), qrPayload(p),
                false, false);
    }

    private static String qrPayload(QrRow p) {
        return "QR".equals(p.method()) ? "MOCKQR|v=1|merchant=" + p.merNo() + "|order=" + p.orderCode()
                + "|amount=" + p.amount() + "|currency=VND" : null;
    }

    private Webhook paid(QrRow p, String transactionRef, Instant paidAt) {
        Map<String, Object> body = webhookBody(p, true, transactionRef, p.amount());
        body.put("paidAt", paidAt.toString());
        return new Webhook(p.webhookUrl(), p.merchantId(), body);
    }

    private Webhook failed(QrRow p, String status) {
        Map<String, Object> body = webhookBody(p, false, "BSFAILED" + System.currentTimeMillis(), 0);
        body.put("status", status);
        return new Webhook(p.webhookUrl(), p.merchantId(), body);
    }

    private static Map<String, Object> webhookBody(QrRow p, boolean success, String transactionRef, long amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", p.tradeNo() + ":" + transactionRef);
        body.put("providerPaymentId", p.tradeNo());
        body.put("gwTxnId", p.tradeNo());
        body.put("orderCode", Long.parseLong(p.orderCode()));
        body.put("success", success);
        body.put("paymentMethod", p.method());
        body.put("threeDs", false);
        body.put("threeDsStatus", "NOT_REQUIRED");
        body.put("amount", amount);
        body.put("transactionRef", transactionRef);

        body.put("payerBankBin", SANDBOX_PAYER_BANK_BIN);
        body.put("payerAccountNumber", SANDBOX_PAYER_ACCOUNT);
        return body;
    }

    private void deliver(Webhook webhook) {
        if (webhook == null) return;
        webhooks.sendQr(webhook.url(), auth.activeSecret(webhook.merchantId()), webhook.body());
    }

    private QrRow require(String tradeNo) {
        QrRow p = row(tradeNo, false);
        if (p == null) throw notFound();
        return p;
    }

    private static ApiException notFound() {
        return new ApiException(404, "PAYMENT_NOT_FOUND", "Payment not found");
    }

    private static void requireOwner(GatewayRuntimeAuth.Access access, QrRow p) {
        if (!access.merchant().getId().equals(p.merchantId()))
            throw new ApiException(403, "MERCHANT_NOT_OWNED", "Resource does not belong to merchant");
    }

    private QrRow byOrder(GatewayRuntimeAuth.Access access, String orderCode) {
        return one("p.merchant_id = ? AND p.terminal_id = ? AND p.order_code = ?", false,
                access.merchant().getId(), access.terminal().getId(), orderCode);
    }

    private QrRow row(String tradeNo, boolean lock) {
        return one("p.provider_payment_id = ?", lock, tradeNo);
    }

    private QrRow one(String where, boolean lock, Object... args) {
        return jdbc.query("""
                SELECT p.provider_payment_id, p.merchant_id, p.order_code, p.amount, p.payment_method, p.status,
                       p.amount_paid, p.transaction_ref, p.paid_at, p.three_ds_result, p.return_url, p.cancel_url,
                       p.expires_at, p.merchant_webhook_url, m.mer_no, m.name AS merchant_name, t.terminal_id AS ter_no,
                       a.code AS acquirer_code
                FROM payment_transactions p
                JOIN merchants m ON m.id = p.merchant_id
                JOIN terminals t ON t.id = p.terminal_id
                LEFT JOIN acquirers a ON a.id = p.selected_acquirer_id
                WHERE p.payment_method <> 'CARD'
                """ + " AND " + where + (lock ? " FOR UPDATE OF p" : ""),
                rs -> rs.next() ? map(rs) : null, args);
    }

    private static QrRow map(ResultSet rs) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new QrRow(rs.getString("provider_payment_id"), rs.getObject("merchant_id", UUID.class),
                rs.getString("mer_no"), rs.getString("ter_no"), rs.getString("merchant_name"), rs.getString("order_code"),
                rs.getLong("amount"), rs.getString("payment_method"), rs.getString("status"), rs.getLong("amount_paid"),
                rs.getString("transaction_ref"), paidAt == null ? null : paidAt.toInstant(), rs.getString("three_ds_result"),
                rs.getString("return_url"), rs.getString("cancel_url"), rs.getTimestamp("expires_at").toInstant(),
                rs.getString("merchant_webhook_url"), rs.getString("acquirer_code"));
    }

    private static void tagLog(QrRow p) {
        GatewayLogContext.setMerchantNo(p.merNo());
        GatewayLogContext.setTerminalNo(p.terNo());
        GatewayLogContext.setTradeNo(p.tradeNo());
        GatewayLogContext.setOrderNo(p.orderCode());
    }

    private record QrRow(String tradeNo, UUID merchantId, String merNo, String terNo, String merchantName,
                         String orderCode, long amount, String method, String status, long amountPaid,
                         String transactionRef, Instant paidAt, String threeDsResult, String returnUrl, String cancelUrl,
                         Instant expiresAt, String webhookUrl, String acquirerCode) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private record Outcome(String redirect, Webhook webhook) {}

    private record Webhook(String url, UUID merchantId, Map<String, Object> body) {}

    public record CheckoutView(String tradeNo, String merchantName, String method, String orderCode, long amount,
                               String status, boolean pending, String backUrl, String qrPayload) {}

    public record PaymentState(String method, String status, String returnUrl, String cancelUrl) {}
}
