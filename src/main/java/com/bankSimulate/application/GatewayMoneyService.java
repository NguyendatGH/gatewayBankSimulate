package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class GatewayMoneyService {
    private final JdbcTemplate jdbc;

    public GatewayMoneyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void register(String providerPaymentId, UUID merchantId, UUID terminalId, String orderCode,
                         long amount, String currency, String method, String acquirerCode, boolean threeDs, String returnUrl,
                         String cancelUrl, Instant expiresAt, String webhookUrl, String bankBin, String accountNumber) {

        if (bankBin == null || accountNumber == null) {
            String[] settlement = jdbc.query("""
                    SELECT CASE WHEN t.settlement_bank_bin IS NOT NULL THEN t.settlement_bank_bin ELSE m.settlement_bank_bin END,
                           CASE WHEN t.settlement_bank_bin IS NOT NULL THEN t.settlement_account_number ELSE m.settlement_account_number END
                    FROM merchants m LEFT JOIN terminals t ON t.id = ? AND t.merchant_id = m.id
                    WHERE m.id = ?
                    """, rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : null, terminalId, merchantId);
            bankBin = settlement == null ? null : settlement[0];
            accountNumber = settlement == null ? null : settlement[1];
        }
        jdbc.update("""
                INSERT INTO payment_transactions
                (id, provider_payment_id, merchant_id, terminal_id, order_code, amount, currency,
                 payment_method, three_ds_requested, status, return_url, cancel_url, expires_at,
                 merchant_webhook_url, settlement_bank_bin, settlement_account_number, selected_acquirer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?,
                    (SELECT id FROM acquirers WHERE code = ?))
                """, UUID.randomUUID(), providerPaymentId, merchantId, terminalId, orderCode, amount,
                currency, method, threeDs, returnUrl, cancelUrl, Timestamp.from(expiresAt), webhookUrl, bankBin, accountNumber,
                acquirerCode);
        jdbc.update("""
                INSERT INTO payment_attempts (id, payment_id, attempt_no, acquirer_id, state)
                SELECT ?, p.id, 1, a.id, 'PENDING' FROM payment_transactions p
                JOIN acquirers a ON a.code = ? WHERE p.provider_payment_id = ?
                """, UUID.randomUUID(), acquirerCode, providerPaymentId);
        event(providerPaymentId, null, "PENDING", "PAYMENT_CREATED");
    }

    @Transactional
    public boolean capture(String providerPaymentId, String transactionRef, Instant paidAt) {
        PaymentRow p = jdbc.queryForObject("""
                SELECT id, merchant_id, amount, status, payment_method, issuer_card_id,
                       settlement_bank_bin, settlement_account_number
                FROM payment_transactions WHERE provider_payment_id = ? FOR UPDATE
                """, (rs, row) -> new PaymentRow(rs.getObject("id", UUID.class),
                rs.getObject("merchant_id", UUID.class), rs.getLong("amount"), rs.getString("status"),
                rs.getString("payment_method"), rs.getObject("issuer_card_id", UUID.class),
                rs.getString("settlement_bank_bin"), rs.getString("settlement_account_number")), providerPaymentId);
        if (p == null) throw new ApiException(404, "PAYMENT_NOT_FOUND", "Payment not registered");
        if ("PAID".equals(p.status)) return true;
        if (!"PENDING".equals(p.status)) throw new ApiException(409, "PAYMENT_NOT_PENDING", "Payment is already final");

        int reserved = jdbc.update("""
                UPDATE issuer_accounts SET available_balance = available_balance - ?, hold_balance = hold_balance + ?,
                updated_at = now() WHERE account_no = 'SANDBOX_BUYER' AND status = 'ACTIVE'
                AND currency = 'VND' AND available_balance >= ?
                """, p.amount, p.amount, p.amount);
        if (reserved == 0) {
            jdbc.update("UPDATE payment_transactions SET status = 'DECLINED', updated_at = now() WHERE id = ?", p.id);
            jdbc.update("UPDATE payment_attempts SET state = 'DECLINED', response_received_at = now(), result_code = 'ISSUER_INSUFFICIENT_FUNDS' WHERE payment_id = ?", p.id);
            event(providerPaymentId, "PENDING", "DECLINED", "ISSUER_INSUFFICIENT_FUNDS");
            return false;
        }
        UUID accountId = jdbc.queryForObject("SELECT id FROM issuer_accounts WHERE account_no = 'SANDBOX_BUYER'", UUID.class);
        jdbc.update("""
                INSERT INTO issuer_holds (id, payment_id, account_id, amount, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), p.id, accountId, p.amount);
        jdbc.update("UPDATE payment_transactions SET status = 'AUTHORIZED', updated_at = now() WHERE id = ?", p.id);
        jdbc.update("UPDATE payment_attempts SET state = 'APPROVED', response_received_at = now(), downstream_reference = ? WHERE payment_id = ?",
                transactionRef, p.id);
        event(providerPaymentId, "PENDING", "AUTHORIZED", "ISSUER_APPROVED");

        jdbc.update("UPDATE issuer_accounts SET balance = balance - ?, hold_balance = hold_balance - ?, updated_at = now() WHERE id = ?",
                p.amount, p.amount, accountId);
        jdbc.update("UPDATE issuer_holds SET status = 'CAPTURED', captured_at = now() WHERE payment_id = ?", p.id);
        event(providerPaymentId, "AUTHORIZED", "CAPTURED", "AUTO_CAPTURE");
        entry(p.id, "CAPTURE", "issuer:SANDBOX_BUYER", -p.amount);
        entry(p.id, "CAPTURE", "clearing:" + p.merchantId, p.amount);

        settle(providerPaymentId, p);
        jdbc.update("""
                UPDATE payment_transactions SET status = 'PAID', amount_paid = ?, transaction_ref = ?,
                paid_at = ?, updated_at = now() WHERE id = ?
                """, p.amount, transactionRef, paidAt == null ? null : Timestamp.from(paidAt), p.id);
        return true;
    }

    @Transactional
    public void markPaid(String providerPaymentId, long amount, String transactionRef, Instant paidAt) {
        int updated = jdbc.update("""
                UPDATE payment_transactions SET status = 'PAID', amount_paid = ?, transaction_ref = ?, paid_at = ?,
                updated_at = now() WHERE provider_payment_id = ? AND status = 'PENDING'
                """, amount, transactionRef, paidAt == null ? null : Timestamp.from(paidAt), providerPaymentId);
        jdbc.update("""
                UPDATE payment_attempts SET state = 'APPROVED', response_received_at = now(), downstream_reference = ?
                WHERE payment_id = (SELECT id FROM payment_transactions WHERE provider_payment_id = ?)
                """, transactionRef, providerPaymentId);
        if (updated == 1) {
            event(providerPaymentId, "PENDING", "PAID", "BANK_APPROVED");
            settleCard(providerPaymentId);
        }
    }

    private void settleCard(String providerPaymentId) {
        PaymentRow p = jdbc.queryForObject("""
                SELECT id, merchant_id, amount, status, payment_method, issuer_card_id,
                       settlement_bank_bin, settlement_account_number
                FROM payment_transactions WHERE provider_payment_id = ?
                """, (rs, row) -> new PaymentRow(rs.getObject("id", UUID.class),
                rs.getObject("merchant_id", UUID.class), rs.getLong("amount"), rs.getString("status"),
                rs.getString("payment_method"), rs.getObject("issuer_card_id", UUID.class),
                rs.getString("settlement_bank_bin"), rs.getString("settlement_account_number")), providerPaymentId);
        String acquirer = jdbc.query("""
                SELECT a.code FROM payment_transactions t JOIN acquirers a ON a.id = t.selected_acquirer_id
                WHERE t.id = ?
                """, rs -> rs.next() ? rs.getString(1) : "UNKNOWN", p.id);
        entry(p.id, "CAPTURE", "acquirer:" + acquirer, -p.amount);
        entry(p.id, "CAPTURE", "clearing:" + p.merchantId, p.amount);
        settle(providerPaymentId, p);
    }

    @Transactional
    public int releaseHeldFunds(UUID merchantId, String bankBin, String accountNumber) {
        return releaseHeld(merchantId, bankBin, accountNumber, """
                AND NOT EXISTS (SELECT 1 FROM terminals t WHERE t.id = p.terminal_id AND t.settlement_bank_bin IS NOT NULL)
                """, merchantId);
    }

    @Transactional
    public int releaseHeldFunds(UUID merchantId, UUID terminalId, String bankBin, String accountNumber) {
        return releaseHeld(merchantId, bankBin, accountNumber, " AND p.terminal_id = ?", merchantId, terminalId);
    }

    private int releaseHeld(UUID merchantId, String bankBin, String accountNumber, String scope, Object... args) {
        List<HeldPayment> held = jdbc.query("""
                SELECT p.id, p.provider_payment_id, p.amount, p.status, p.payment_method, p.issuer_card_id
                FROM payment_transactions p
                WHERE p.merchant_id = ? AND p.status = 'PAID' AND p.settlement_bank_bin IS NULL
                  AND EXISTS (SELECT 1 FROM sandbox_ledger_entries l WHERE l.payment_id = p.id
                              AND l.phase = 'CAPTURE' AND l.account_key = 'clearing:' || p.merchant_id)
                  AND NOT EXISTS (SELECT 1 FROM sandbox_ledger_entries l WHERE l.payment_id = p.id AND l.phase = 'SETTLEMENT')
                """ + scope + "\n" + """
                ORDER BY p.created_at
                FOR UPDATE OF p
                """, (rs, n) -> new HeldPayment(rs.getString("provider_payment_id"), new PaymentRow(
                rs.getObject("id", UUID.class), merchantId, rs.getLong("amount"), rs.getString("status"),
                rs.getString("payment_method"), rs.getObject("issuer_card_id", UUID.class), bankBin, accountNumber)),
                args);
        for (HeldPayment h : held) {
            jdbc.update("""
                    UPDATE payment_transactions SET settlement_bank_bin = ?, settlement_account_number = ?, updated_at = now()
                    WHERE id = ?
                    """, bankBin, accountNumber, h.row().id);
            settle(h.providerPaymentId(), h.row(), "HELD_FUNDS_RELEASED");
        }
        return held.size();
    }

    private record HeldPayment(String providerPaymentId, PaymentRow row) {}

    private void settle(String providerPaymentId, PaymentRow p) {
        settle(providerPaymentId, p, "DEFAULT_MERCHANT_ACCOUNT");
    }

    private void settle(String providerPaymentId, PaymentRow p, String reason) {
        if (p.bankBin == null || p.accountNumber == null) {
            event(providerPaymentId, "CAPTURED", "PAID", "SETTLEMENT_DESTINATION_NOT_CONFIGURED");
            return;
        }
        jdbc.update("""
                INSERT INTO sandbox_merchant_accounts (merchant_id, bank_bin, account_number, balance)
                VALUES (?, ?, ?, ?) ON CONFLICT (merchant_id, bank_bin, account_number)
                DO UPDATE SET balance = sandbox_merchant_accounts.balance + EXCLUDED.balance
                """, p.merchantId, p.bankBin, p.accountNumber, p.amount);
        entry(p.id, "SETTLEMENT", "clearing:" + p.merchantId, -p.amount);
        entry(p.id, "SETTLEMENT", "merchant:" + p.merchantId + ":" + p.bankBin + ":" + p.accountNumber, p.amount);
        event(providerPaymentId, "CAPTURED", "SETTLED", reason);
    }

    @Transactional
    public boolean markFinal(String providerPaymentId, String status) {
        int updated = jdbc.update("UPDATE payment_transactions SET status = ?, updated_at = now() WHERE provider_payment_id = ? AND status = 'PENDING'",
                status, providerPaymentId);
        if (updated != 1) return false;
        jdbc.update("UPDATE payment_attempts SET state = ?, response_received_at = now(), result_code = ? WHERE payment_id = (SELECT id FROM payment_transactions WHERE provider_payment_id = ?)",
                status, status, providerPaymentId);
        event(providerPaymentId, "PENDING", status, "PAYMENT_FINALIZED");
        return true;
    }

    public java.util.Optional<ExistingPayment> findByOrder(UUID merchantId, String orderCode) {
        return java.util.Optional.ofNullable(jdbc.query("""
                SELECT provider_payment_id, payment_method FROM payment_transactions
                WHERE merchant_id = ? AND order_code = ?
                ORDER BY created_at LIMIT 1
                """, rs -> rs.next() ? new ExistingPayment(rs.getString(1), rs.getString(2)) : null,
                merchantId, orderCode));
    }

    public record ExistingPayment(String providerPaymentId, String paymentMethod) {}

    @Transactional
    public void updateThreeDs(String providerPaymentId, String result) {
        jdbc.update("UPDATE payment_transactions SET three_ds_result = ?, updated_at = now() WHERE provider_payment_id = ?",
                result, providerPaymentId);
        event(providerPaymentId, "PENDING", "3DS_" + result, "3DS_" + result);
    }

    public PersistentPaymentStatus status(String providerPaymentId) {
        return jdbc.query("""
                SELECT merchant_id, status, amount_paid, transaction_ref, paid_at, three_ds_requested, three_ds_result
                FROM payment_transactions WHERE provider_payment_id = ?
                """, rs -> rs.next() ? new PersistentPaymentStatus(rs.getObject("merchant_id", UUID.class),
                rs.getString("status"), rs.getLong("amount_paid"), rs.getString("transaction_ref"),
                rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
                rs.getBoolean("three_ds_requested"), rs.getString("three_ds_result")) : null, providerPaymentId);
    }

    private void entry(UUID paymentId, String phase, String account, long amount) {

        jdbc.update("INSERT INTO sandbox_ledger_entries (id, payment_id, phase, account_key, amount, created_at) VALUES (?, ?, ?, ?, ?, clock_timestamp())",
                UUID.randomUUID(), paymentId, phase, account, amount);
    }

    private void event(String providerPaymentId, String fromState, String toState, String type) {
        UUID paymentId = jdbc.query("SELECT id FROM payment_transactions WHERE provider_payment_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, providerPaymentId);
        if (paymentId == null) return;

        jdbc.update("""
                INSERT INTO transaction_events (id, payment_id, from_state, to_state, event_type, event_data, created_at)
                VALUES (?, ?, ?, ?, ?, '{}'::jsonb, clock_timestamp())
                """, UUID.randomUUID(), paymentId, fromState, toState, type);
    }

    private record PaymentRow(UUID id, UUID merchantId, long amount, String status, String paymentMethod,
                              UUID issuerCardId, String bankBin, String accountNumber) {}

    public record PersistentPaymentStatus(UUID merchantId, String status, long amountPaid, String transactionRef,
                                          Instant paidAt, boolean threeDsRequested, String threeDsResult) {}
}
