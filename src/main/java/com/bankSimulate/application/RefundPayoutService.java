package com.bankSimulate.application;

import com.bankSimulate.domain.bank.BankRefundResult;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.persistence.GatewayTransactionRepository;
import com.bankSimulate.infrastructure.web.dto.GatewayRuntimeDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class RefundPayoutService {

    private static final Logger log = LoggerFactory.getLogger(RefundPayoutService.class);

    private final JdbcTemplate jdbc;
    private final BankService banks;
    private final GatewayTransactionRepository transactions;
    private final long payoutBalance;

    private final Object lock = new Object();

    public RefundPayoutService(JdbcTemplate jdbc, BankService banks, GatewayTransactionRepository transactions,
                               @Value("${gateway.payout-balance:100000000}") long payoutBalance) {
        this.jdbc = jdbc;
        this.banks = banks;
        this.transactions = transactions;
        this.payoutBalance = payoutBalance;
    }

    public GatewayRuntimeDtos.RefundResponse submit(GatewayRuntimeAuth.Access access, GatewayRuntimeDtos.RefundRequest request) {

        GatewayLogContext.setOrderNo(request.referenceId());
        UUID merchantId = access.merchant().getId();
        synchronized (lock) {
            RefundRow existing = byReference(merchantId, request.referenceId());
            if (existing != null) return replay(existing, request);

            UUID paymentId = requirePayable(merchantId, request.providerPaymentId(), request.amount());
            long available = payoutBalance();
            if (request.amount() > available)
                throw new ApiException(409, "INSUFFICIENT_PAYOUT_BALANCE",
                        "Payout wallet has " + available + ", refund needs " + request.amount());

            BankRefundResult result = banks.refund(request.referenceId(), request.amount(), request.toBin(),
                    request.toAccountNumber());
            if (!"SUCCEEDED".equals(result.status())) {
                log.info("Refund {} amount={} toBin={} failureCode={}", result.status(), request.amount(),
                        request.toBin(), result.failureCode());
                throw new ApiException(409, result.failureCode() == null ? "REFUND_FAILED" : result.failureCode(),
                        result.failureReason() == null ? "Bank rejected refund" : result.failureReason());
            }

            String tradeNo = transactions.nextTradeNo();
            GatewayLogContext.setTradeNo(tradeNo);
            try {
                jdbc.update("""
                        INSERT INTO refund_transactions (id, provider_refund_id, payment_id, merchant_id, merchant_reference,
                            amount, status, idempotency_key, to_bank_bin, to_account_number, description, bank_reference)
                        VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), tradeNo, paymentId, merchantId, request.referenceId(), request.amount(),
                        request.referenceId(), request.toBin(), request.toAccountNumber(), request.description(),
                        result.providerRefundId());
            } catch (DuplicateKeyException raced) {

                return replay(byReference(merchantId, request.referenceId()), request);
            }
            log.info("Refund SUCCEEDED amount={} toBin={} payment={} bankRef={}", request.amount(), request.toBin(),
                    request.providerPaymentId(), result.providerRefundId());
            return byProviderRefundId(tradeNo).response();
        }
    }

    public GatewayRuntimeDtos.RefundResponse status(GatewayRuntimeAuth.Access access, String providerRefundId) {
        RefundRow row = byProviderRefundId(providerRefundId);
        if (row == null) throw new ApiException(404, "REFUND_NOT_FOUND", "Refund not found");
        if (!row.merchantId().equals(access.merchant().getId()))
            throw new ApiException(403, "MERCHANT_NOT_OWNED", "Resource does not belong to merchant");
        GatewayLogContext.setTradeNo(row.providerRefundId());
        return row.response();
    }

    public GatewayRuntimeDtos.RefundResponse byReference(GatewayRuntimeAuth.Access access, String referenceId) {
        RefundRow row = byReference(access.merchant().getId(), referenceId);
        return row == null ? null : row.response();
    }

    public long payoutBalance() {
        Long spent = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refund_transactions WHERE status = 'SUCCEEDED'", Long.class);
        return Math.max(0, payoutBalance - (spent == null ? 0 : spent));
    }

    private UUID requirePayable(UUID merchantId, String providerPaymentId, long amount) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) return null;
        PaymentRow p = jdbc.query("""
                SELECT id, merchant_id, status, amount_paid FROM payment_transactions WHERE provider_payment_id = ?
                """, rs -> rs.next() ? new PaymentRow(rs.getObject("id", UUID.class), rs.getObject("merchant_id", UUID.class),
                rs.getString("status"), rs.getLong("amount_paid")) : null, providerPaymentId);
        if (p == null) throw new ApiException(404, "PAYMENT_NOT_FOUND", "Payment " + providerPaymentId + " not found");
        if (!p.merchantId().equals(merchantId))
            throw new ApiException(403, "MERCHANT_NOT_OWNED", "Payment belongs to another merchant");
        if (!"PAID".equals(p.status()))
            throw new ApiException(409, "PAYMENT_NOT_REFUNDABLE", "Payment is " + p.status() + ", only PAID can be refunded");
        Long refunded = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM refund_transactions WHERE payment_id = ? AND status = 'SUCCEEDED'
                """, Long.class, p.id());
        long already = refunded == null ? 0 : refunded;
        if (already + amount > p.amountPaid())
            throw new ApiException(409, "REFUND_EXCEEDS_PAYMENT", "Payment " + providerPaymentId + " paid "
                    + p.amountPaid() + ", already refunded " + already + ", cannot refund " + amount + " more");
        return p.id();
    }

    private static GatewayRuntimeDtos.RefundResponse replay(RefundRow existing, GatewayRuntimeDtos.RefundRequest request) {
        if (existing.amount() != request.amount() || !Objects.equals(existing.toAccount(), request.toAccountNumber()))
            throw new ApiException(409, "REFUND_REFERENCE_CONFLICT", "Refund reference was already used with different details");
        GatewayLogContext.setTradeNo(existing.providerRefundId());
        log.info("Refund reference replayed, returning existing {}", existing.status());
        return existing.response();
    }

    private RefundRow byReference(UUID merchantId, String referenceId) {
        return one("merchant_id = ? AND idempotency_key = ?", merchantId, referenceId);
    }

    private RefundRow byProviderRefundId(String providerRefundId) {
        return one("provider_refund_id = ?", providerRefundId);
    }

    private RefundRow one(String where, Object... args) {
        return jdbc.query("SELECT provider_refund_id, merchant_id, amount, status, to_account_number, failure_code, "
                        + "failure_reason FROM refund_transactions WHERE " + where,
                rs -> rs.next() ? new RefundRow(rs.getString("provider_refund_id"), rs.getObject("merchant_id", UUID.class),
                        rs.getLong("amount"), rs.getString("status"), rs.getString("to_account_number"),
                        rs.getString("failure_code"), rs.getString("failure_reason")) : null, args);
    }

    private record PaymentRow(UUID id, UUID merchantId, String status, long amountPaid) {}

    private record RefundRow(String providerRefundId, UUID merchantId, long amount, String status, String toAccount,
                             String failureCode, String failureReason) {
        GatewayRuntimeDtos.RefundResponse response() {
            return new GatewayRuntimeDtos.RefundResponse(providerRefundId, status, failureCode, failureReason);
        }
    }
}
