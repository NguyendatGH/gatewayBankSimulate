package com.bankSimulate.application;

import com.bankSimulate.domain.cardAuth.*;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.common.ResultCode;
import com.bankSimulate.domain.enums.Actor;
import com.bankSimulate.domain.enums.GateWayTransactionStatus;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.ThreeDsPreference;
import com.bankSimulate.domain.gateway.GatewayTransaction;
import com.bankSimulate.domain.gateway.GatewayTransactionEvent;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.notify.MerchantWebhookSender;
import com.bankSimulate.infrastructure.persistence.GatewayTransactionEventRepository;
import com.bankSimulate.infrastructure.persistence.GatewayTransactionRepository;
import com.bankSimulate.infrastructure.persistence.TerminalPaymentMethodRepository;
import com.bankSimulate.infrastructure.web.dto.CardPaymentDtos;
import com.bankSimulate.infrastructure.web.dto.GatewayRuntimeDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CardPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CardPaymentService.class);

    private final GatewayRuntimeAuth auth;
    private final MerchantService merchants;
    private final TerminalService terminals;
    private final CardBankRouter router;
    private final TerminalPaymentMethodRepository methods;
    private final GatewayTransactionRepository transactions;
    private final GatewayTransactionEventRepository events;
    private final MerchantWebhookSender webhooks;
    private final GatewayMoneyService money;
    private final TransactionTemplate tx;
    private final String publicBaseUrl;
    private final Duration authTtl;

    public CardPaymentService(GatewayRuntimeAuth auth, MerchantService merchants, TerminalService terminals, CardBankRouter router,
                              TerminalPaymentMethodRepository methods, GatewayTransactionRepository transactions,
                              GatewayTransactionEventRepository events, MerchantWebhookSender webhooks,
                              GatewayMoneyService money, TransactionTemplate tx,
                              @Value("${gateway.public-base-url:http://localhost:8090}") String publicBaseUrl,
                              @Value("${gateway.card.auth-ttl:PT15M}") Duration authTtl) {
        this.auth = auth;
        this.merchants = merchants;
        this.terminals = terminals;
        this.router = router;
        this.methods = methods;
        this.transactions = transactions;
        this.events = events;
        this.webhooks = webhooks;
        this.money = money;
        this.tx = tx;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        this.authTtl = authTtl;
    }

    public CardPaymentDtos.CreateResponse create(GatewayRuntimeAuth.Access access,
                                                 CardPaymentDtos.CreateRequest request) {
        GatewayLogContext.setOrderNo(request.orderCode());
        if (!methods.existsByTerminalIdAndPaymentMethod(access.terminal().getId(), PaymentMethod.CARD))
            throw new ApiException(409, "PAYMENT_METHOD_NOT_ENABLED", "CARD is not enabled on terminal");

        var existing = transactions.findByMerchantIdAndTerminalIdAndOrderCode(
                access.merchant().getId(), access.terminal().getId(), request.orderCode());
        if (existing.isPresent()) {
            GatewayLogContext.setTradeNo(existing.get().getGwTxnId());
            log.info("Duplicate orderCode, returning existing transaction status={}", existing.get().getStatus());
            return response(existing.get(), null);
        }

        CardBankRouter.CardRoute bank = router.resolve(access.merchant(), access.terminal());
        String gwTxnId = transactions.nextTradeNo();
        GatewayLogContext.setTradeNo(gwTxnId);

        tx.executeWithoutResult(status -> {
            GatewayTransaction created = new GatewayTransaction(gwTxnId, access.merchant().getId(),
                    access.terminal().getId(), request.orderCode(), request.amount(),
                    access.terminal().getCurrency(), PaymentMethod.CARD.name(), bank.acquirerCode(),
                    ThreeDsPreference.from(access.terminal().getThreeDsPolicy()),
                    request.returnUrl(), webhookUrlFor(access, request), Instant.now().plus(authTtl));
            transactions.save(created);
            record(created, null, Actor.MERCHANT, "Created for bank " + bank.acquirerCode()
                    + " amount=" + request.amount() + " cardSupplied=" + request.hasCard());
        });

        if (!request.hasCard()) {
            return new CardPaymentDtos.CreateResponse(gwTxnId, null, GateWayTransactionStatus.CREATED.name(),
                    publicBaseUrl + "/card-checkout/" + gwTxnId, null, null, null,
                    "Chuyển chủ thẻ sang checkoutUrl để nhập thẻ");
        }

        String redirectUrl = authorizeWithBank(gwTxnId, request.pan(), request.expiryMonth(),
                request.expiryYear(), request.cvv());
        return response(require(gwTxnId), redirectUrl);
    }

    public GatewayRuntimeDtos.PaymentResponse createFromPayments(GatewayRuntimeAuth.Access access,
                                                                 GatewayRuntimeDtos.CreatePaymentRequest request) {
        CardPaymentDtos.CreateResponse created = create(access, new CardPaymentDtos.CreateRequest(
                Long.toString(request.orderCode()), request.amount(), null, null, null, null, request.returnUrl(), null));
        return new GatewayRuntimeDtos.PaymentResponse(created.gwTxnId(), created.checkoutUrl(), null);
    }

    public CardPaymentDtos.StatusResponse status(GatewayRuntimeAuth.Access access, String gwTxnId) {
        GatewayTransaction txn = owned(access, gwTxnId);
        tagLog(access.merchant().getMerNo(), access.terminal().getTerminalId(), txn);
        return new CardPaymentDtos.StatusResponse(txn.getGwTxnId(), txn.getOrderCode(), txn.getAmount(),
                txn.getResultCode(), txn.getStatus().name(), txn.getBankCode(), txn.getBankRef(),
                txn.getAuthCode(), txn.getCardBrand(), txn.getCardMasked(), txn.getFailureReason(), txn.getPaidAt());
    }

    public boolean exists(String tradeNo) {
        return transactions.existsByGwTxnId(tradeNo);
    }

    public Optional<GatewayRuntimeDtos.PaymentStatusResponse> paymentStatus(GatewayRuntimeAuth.Access access,
                                                                         String tradeNo) {
        return transactions.findByGwTxnId(tradeNo).map(txn -> {
            requireOwner(access, txn);
            tagLog(access.merchant().getMerNo(), access.terminal().getTerminalId(), txn);
            boolean paid = txn.getStatus() == GateWayTransactionStatus.SUCCESS;
            return new GatewayRuntimeDtos.PaymentStatusResponse(paymentStatusOf(txn), paid ? txn.getAmount() : 0,
                    txn.getBankRef(), txn.getPaidAt());
        });
    }

    private static String paymentStatusOf(GatewayTransaction txn) {
        return switch (txn.getStatus()) {
            case CREATED, PENDING_AUTH -> "PENDING";
            case SUCCESS -> "PAID";
            case EXPIRED -> "EXPIRED";
            case FAILED -> ResultCode.CANCELLED.equals(txn.getResultCode()) ? "CANCELLED" : "DECLINED";
        };
    }

    public void cancel(GatewayRuntimeAuth.Access access, String gwTxnId) {
        tagLog(owned(access, gwTxnId));
        Notification notification = tx.execute(status -> {
            GatewayTransaction txn = transactions.findWithLockByGwTxnId(gwTxnId).orElseThrow();
            if (txn.isFinal()) return null;
            return apply(txn, ResultCode.CANCELLED, null, null, "Cancelled by merchant",
                    Actor.MERCHANT);
        });
        deliver(notification);
    }

    public String submitCard(String gwTxnId, String pan, int expiryMonth, int expiryYear, String cvv) {
        GatewayTransaction txn = require(gwTxnId);
        tagLog(txn);
        if (txn.getStatus() != GateWayTransactionStatus.CREATED)
            throw new ApiException(409, "CARD_ALREADY_SUBMITTED", "Transaction is " + txn.getStatus());
        if (txn.isExpired(Instant.now())) throw new ApiException(409, "TRANSACTION_EXPIRED", "Transaction expired");

        String redirectUrl = authorizeWithBank(gwTxnId, pan, expiryMonth, expiryYear, cvv);
        return redirectUrl != null ? redirectUrl : publicBaseUrl + "/gateway-return/" + gwTxnId;
    }

    public GatewayTransaction require(String gwTxnId) {
        return transactions.findByGwTxnId(gwTxnId)
                .orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "Transaction not found"));
    }

    public void handleBankNotification(String bankCode, String bankRef, boolean approved,
                                       String authCode, String message) {
        GatewayLogContext.setBank(bankCode);
        Notification notification = tx.execute(status -> {
            GatewayTransaction txn = transactions.findWithLockByBankCodeAndBankRef(bankCode, bankRef)
                    .orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND",
                            "No transaction for " + bankCode + "/" + bankRef));
            tagLog(txn);
            log.info("Bank notification received bankRef={} approved={} message={}", bankRef, approved, message);
            if (txn.isFinal()) {
                log.info("Bank notification ignored, transaction already {} bankRef={}", txn.getStatus(), bankRef);
                return null;
            }
            return approved
                    ? apply(txn, ResultCode.SUCCESS, bankRef, authCode, message, Actor.BANK)
                    : apply(txn, ResultCode.FAILED, bankRef, null, message, Actor.BANK);
        });
        deliver(notification);
    }

    public int expireStale() {
        List<GatewayTransaction> stale = transactions.findTop50ByStatusInAndExpiresAtBefore(
                List.of(GateWayTransactionStatus.CREATED, GateWayTransactionStatus.PENDING_AUTH), Instant.now());
        int expired = 0;
        for (GatewayTransaction candidate : stale) {

            try (GatewayLogContext.Scope ignored = GatewayLogContext.withBank(GatewayLogContext.GATEWAY)) {
                tagLog(candidate);
                Notification notification = tx.execute(status -> {
                    GatewayTransaction txn = transactions.findWithLockByGwTxnId(candidate.getGwTxnId()).orElseThrow();
                    if (txn.isFinal() || !txn.isExpired(Instant.now())) return null;
                    var from = txn.getStatus();
                    txn.expire();
                    record(txn, from, Actor.JOB, "Expired after waiting for cardholder");
                    return notificationOf(txn);
                });
                if (notification != null) {
                    deliver(notification);
                    expired++;
                }
            }
        }
        return expired;
    }

    private String authorizeWithBank(String gwTxnId, String pan, int expiryMonth, int expiryYear, String cvv) {
        GatewayTransaction snapshot = require(gwTxnId);
        Merchant merchant = merchants.requireById(snapshot.getMerchantId());
        Terminal terminal = terminals.requireById(snapshot.getTerminalId());
        List<CardBankRouter.CardRoute> chain = failoverChain(merchant, terminal, snapshot.getBankCode());
        tagLog(merchant.getMerNo(), terminal.getTerminalId(), snapshot);

        tx.executeWithoutResult(status -> transactions.findWithLockByGwTxnId(gwTxnId)
                .orElseThrow().recordCard(cardBrand(pan), mask(pan)));

        CardAuthorizationResult answered = null;
        ApiException lastFailure = null;

        for (int i = 0; i < chain.size(); i++) {
            CardBankRouter.CardRoute bank = chain.get(i);

            try (GatewayLogContext.Scope ignored = GatewayLogContext.withBank(bank.acquirerCode())) {
                useBank(gwTxnId, bank.acquirerCode());
                log.info("Authorizing with bank={} amount={} threeDsPreference={}",
                        bank.acquirerCode(), snapshot.getAmount(), snapshot.getThreeDsPreference());
                try {
                    answered = bank.port().authorize(bank.acquirerCode(), new CardAuthorizationCommand(gwTxnId, merchant.getMerNo(),
                            snapshot.getAmount(), snapshot.getCurrency(), pan, expiryMonth, expiryYear, cvv,
                            snapshot.getThreeDsPreference(),
                            publicBaseUrl + "/gateway-callback/" + bank.acquirerCode(),
                            publicBaseUrl + "/gateway-return/" + gwTxnId));
                    log.info("Bank answered outcome={} bankRawCode={} bankRef={}",
                            answered.outcome(), answered.bankRawCode(), answered.bankRef());
                    break;
                } catch (ApiException failure) {
                    lastFailure = failure;
                    if (!BankFailure.canTryAnotherBank(failure.getCode())) {
                        log.warn("Bank {} lỗi {} — KHÔNG được thử bank khác, chốt FAILED luôn",
                                bank.acquirerCode(), failure.getCode());
                        failNow(gwTxnId, failure.getMessage());
                        throw failure;
                    }
                    String nextBank = i + 1 < chain.size() ? chain.get(i + 1).acquirerCode() : null;
                    log.warn("Bank {} không nhận được lệnh ({}) — {}", bank.acquirerCode(), failure.getCode(),
                            nextBank == null ? "hết bank để thử" : "chuyển sang " + nextBank);
                    noteFailover(gwTxnId, bank.acquirerCode(), failure, nextBank);
                }
            }
        }

        if (answered == null) {
            String reason = "Đã thử " + chain.size() + " bank, không bank nào nhận được lệnh. Lỗi cuối: "
                    + (lastFailure == null ? "-" : lastFailure.getMessage());
            log.error("{}", reason);
            failNow(gwTxnId, reason);
            throw new ApiException(502, BankFailure.ALL_UNAVAILABLE, reason);
        }

        final CardAuthorizationResult result = answered;

        Notification notification = tx.execute(status -> {
            GatewayTransaction txn = transactions.findWithLockByGwTxnId(gwTxnId).orElseThrow();
            return switch (result.outcome()) {
                case APPROVED -> apply(txn, ResultCode.SUCCESS, result.bankRef(), result.authCode(),
                        result.msg(), Actor.BANK);
                case DECLINED -> apply(txn, ResultCode.FAILED, result.bankRef(), null,
                        result.msg(), Actor.BANK);
                case REDIRECT_REQUIRED -> {
                    var from = txn.getStatus();
                    txn.awaitAuthentication(result.bankRef());
                    record(txn, from, Actor.BANK, "Bank requires cardholder authentication");
                    yield null;
                }
            };
        });
        deliver(notification);
        return result.redirectUrl();
    }

    private List<CardBankRouter.CardRoute> failoverChain(Merchant merchant, Terminal terminal, String currentBankCode) {
        List<CardBankRouter.CardRoute> all = router.resolveAll(merchant, terminal);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).acquirerCode().equals(currentBankCode)) return all.subList(i, all.size());
        }
        return List.of(router.routeFor(currentBankCode));
    }

    private void useBank(String gwTxnId, String bankCode) {
        tx.executeWithoutResult(status -> {
            GatewayTransaction txn = transactions.findWithLockByGwTxnId(gwTxnId).orElseThrow();
            if (!bankCode.equals(txn.getBankCode())) txn.switchBank(bankCode);
        });
    }

    private void noteFailover(String gwTxnId, String fromBank, ApiException failure, String nextBank) {
        String detail = "Bank " + fromBank + " lỗi " + failure.getCode()
                + (nextBank == null ? ", hết bank để thử" : ", chuyển sang " + nextBank);
        tx.executeWithoutResult(status -> {
            GatewayTransaction txn = transactions.findWithLockByGwTxnId(gwTxnId).orElseThrow();
            events.save(new GatewayTransactionEvent(txn.getId(), txn.getStatus(), txn.getStatus(),
                    Actor.GATEWAY, detail));
        });
    }

    private void failNow(String gwTxnId, String reason) {
        deliver(tx.execute(status -> apply(transactions.findWithLockByGwTxnId(gwTxnId).orElseThrow(),
                ResultCode.FAILED, null, null, reason, Actor.GATEWAY)));
    }

    private Notification apply(GatewayTransaction txn, String resultCode, String bankRef, String authCode,
                               String message, Actor source) {
        var from = txn.getStatus();
        boolean changed = ResultCode.SUCCESS.equals(resultCode)
                ? txn.succeed(bankRef, authCode)
                : txn.fail(bankRef, resultCode, message);
        if (!changed) return null;
        record(txn, from, source, message);
        return notificationOf(txn);
    }

    private void record(GatewayTransaction txn, GateWayTransactionStatus from, Actor actor, String detail) {
        events.save(new GatewayTransactionEvent(txn.getId(), from, txn.getStatus(), actor, detail));
        log.info("Status {} -> {} by {} resultCode={}: {}", from, txn.getStatus(), actor, txn.getResultCode(), detail);
        syncPaymentRecord(txn, from);
    }

    private void syncPaymentRecord(GatewayTransaction txn, GateWayTransactionStatus from) {
        String tradeNo = txn.getGwTxnId();
        switch (txn.getStatus()) {
            case CREATED -> money.register(tradeNo, txn.getMerchantId(), txn.getTerminalId(), txn.getOrderCode(),
                    txn.getAmount(), txn.getCurrency(), txn.getPaymentMethod(), txn.getBankCode(), false,
                    txn.getMerchantReturnUrl(), null, txn.getExpiresAt(), txn.getMerchantWebhookUrl(), null, null);
            case PENDING_AUTH -> money.updateThreeDs(tradeNo, "CHALLENGE");
            case SUCCESS -> {
                if (from == GateWayTransactionStatus.PENDING_AUTH) money.updateThreeDs(tradeNo, "AUTHENTICATED");
                money.markPaid(tradeNo, txn.getAmount(), txn.getBankRef(), txn.getPaidAt());
            }
            case FAILED -> money.markFinal(tradeNo,
                    ResultCode.CANCELLED.equals(txn.getResultCode()) ? "CANCELLED" : "DECLINED");
            case EXPIRED -> money.markFinal(tradeNo, "EXPIRED");
        }
    }

    private void tagLog(GatewayTransaction txn) {
        tagLog(merchants.requireById(txn.getMerchantId()).getMerNo(),
                terminals.requireById(txn.getTerminalId()).getTerminalId(), txn);
    }

    private static void tagLog(String merNo, String terNo, GatewayTransaction txn) {
        GatewayLogContext.setMerchantNo(merNo);
        GatewayLogContext.setTerminalNo(terNo);
        GatewayLogContext.setTradeNo(txn.getGwTxnId());
        GatewayLogContext.setOrderNo(txn.getOrderCode());
    }

    private Notification notificationOf(GatewayTransaction txn) {
        Merchant merchant = merchants.requireById(txn.getMerchantId());
        return new Notification(txn.getMerchantWebhookUrl(), auth.activeSecret(merchant), txn.getGwTxnId(),
                txn.getOrderCode(), txn.getResultCode(), txn.getBankRef(), txn.getAuthCode(), txn.getAmount(),
                txn.getFailureReason(), txn.getPaidAt());
    }

    private void deliver(Notification notification) {
        if (notification == null) return;
        webhooks.send(notification.webhookUrl(), notification.merchantSecret(), notification.gwTxnId(),
                notification.orderCode(), notification.resultCode(), notification.bankRef(),
                notification.authCode(), notification.amount(), notification.message(), notification.paidAt());
    }

    private static String webhookUrlFor(GatewayRuntimeAuth.Access access, CardPaymentDtos.CreateRequest request) {
        return request.webhookUrl() != null && !request.webhookUrl().isBlank()
                ? request.webhookUrl() : access.merchant().getWebhookUrl();
    }

    private GatewayTransaction owned(GatewayRuntimeAuth.Access access, String gwTxnId) {
        GatewayTransaction txn = require(gwTxnId);
        requireOwner(access, txn);
        return txn;
    }

    private static void requireOwner(GatewayRuntimeAuth.Access access, GatewayTransaction txn) {
        if (!txn.getMerchantId().equals(access.merchant().getId()))
            throw new ApiException(403, "TRANSACTION_NOT_OWNED", "Transaction belongs to another merchant");
    }

    private CardPaymentDtos.CreateResponse response(GatewayTransaction txn, String redirectUrl) {
        String checkoutUrl = txn.getStatus() == GateWayTransactionStatus.CREATED
                ? publicBaseUrl + "/card-checkout/" + txn.getGwTxnId() : null;
        return new CardPaymentDtos.CreateResponse(txn.getGwTxnId(), txn.getResultCode(), txn.getStatus().name(),
                checkoutUrl, redirectUrl, txn.getBankRef(), txn.getCardMasked(), txn.getFailureReason());
    }

    static String mask(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }

    static String cardBrand(String pan) {
        if (pan == null || pan.isEmpty()) return "UNKNOWN";
        return switch (pan.charAt(0)) {
            case '4' -> "VISA";
            case '5' -> "MASTERCARD";
            case '3' -> "AMEX";
            default -> "UNKNOWN";
        };
    }

    private record Notification(String webhookUrl, String merchantSecret, String gwTxnId, String orderCode,
                                String resultCode, String bankRef, String authCode, long amount, String message,
                                Instant paidAt) {}
}
