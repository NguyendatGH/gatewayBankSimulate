package com.bankSimulate.domain.gateway;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.common.ResultCode;
import com.bankSimulate.domain.enums.GateWayTransactionStatus;
import com.bankSimulate.domain.enums.ThreeDsPreference;
import jakarta.persistence.*;
import lombok.Getter;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "gateway_transactions")
public class GatewayTransaction {

    @Id
    private UUID id;

    @Column(name = "gw_txn_id", nullable = false, unique = true, length = 64)
    private String gwTxnId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Column(name = "order_code", nullable = false, length = 128)
    private String orderCode;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "three_ds_preference", length = 24)
    private ThreeDsPreference threeDsPreference;

    @Column(name = "bank_code", nullable = false, length = 64)
    private String bankCode;

    @Column(name = "bank_ref", length = 96)
    private String bankRef;

    @Column(name = "auth_code", length = 64)
    private String authCode;

    @Column(name = "result_code", length = 4)
    private String resultCode;

    @Column(name = "result_message")
    private String resultMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GateWayTransactionStatus status;

    @Column(name = "card_brand", length = 24)
    private String cardBrand;

    @Column(name = "card_masked", length = 24)
    private String cardMasked;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "merchant_return_url", nullable = false, length = 2048)
    private String merchantReturnUrl;

    @Column(name = "merchant_webhook_url", length = 2048)
    private String merchantWebhookUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected GatewayTransaction() {}

    public GatewayTransaction(String gwTxnId, UUID merchantId, UUID terminalId, String orderCode, long amount,
                              String currency, String paymentMethod, String bankCode,
                              ThreeDsPreference threeDsPreference,
                              String merchantReturnUrl, String merchantWebhookUrl, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.gwTxnId = gwTxnId;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.orderCode = orderCode;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.bankCode = bankCode;
        this.threeDsPreference = threeDsPreference;
        this.merchantReturnUrl = merchantReturnUrl;
        this.merchantWebhookUrl = merchantWebhookUrl;
        this.expiresAt = expiresAt;
        this.status = GateWayTransactionStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void recordCard(String brand, String maskedPan) {
        this.cardBrand = brand;
        this.cardMasked = maskedPan;
        touch();
    }

    public void switchBank(String bankCode) {
        requireStatus(GateWayTransactionStatus.CREATED, "BANK_SWITCH_NOT_ALLOWED");
        this.bankCode = bankCode;
        touch();
    }

    public void awaitAuthentication(String bankRef) {
        requireStatus(GateWayTransactionStatus.CREATED, "TRANSACTION_NOT_PENDING");
        this.bankRef = bankRef;
        this.resultCode = ResultCode.PENDING_AUTH;
        this.status = GateWayTransactionStatus.PENDING_AUTH;
        touch();
    }

    public boolean succeed(String bankRef, String authCode) {
        if (status == GateWayTransactionStatus.SUCCESS) return false;
        requireNotFinal();
        this.bankRef = bankRef == null ? this.bankRef : bankRef;
        this.authCode = authCode;
        this.resultCode = ResultCode.SUCCESS;
        this.status = GateWayTransactionStatus.SUCCESS;
        this.paidAt = Instant.now();
        touch();
        return true;
    }

    public boolean fail(String bankRef, String resultCode, String reason) {
        if (status == GateWayTransactionStatus.FAILED) return false;
        requireNotFinal();
        if (bankRef != null) this.bankRef = bankRef;
        this.resultCode = resultCode;
        this.failureReason = reason;
        this.status = GateWayTransactionStatus.FAILED;
        touch();
        return true;
    }

    public boolean expire() {
        if (isFinal()) return false;
        this.resultCode = ResultCode.EXPIRED;
        this.failureReason = "Hết thời gian chờ chủ thẻ xác thực";
        this.status = GateWayTransactionStatus.EXPIRED;
        touch();
        return true;
    }

    public boolean isFinal() {
        return status == GateWayTransactionStatus.SUCCESS || status == GateWayTransactionStatus.FAILED || status == GateWayTransactionStatus.EXPIRED;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requireStatus(GateWayTransactionStatus expected, String code) {
        if (status != expected) throw new ApiException(409, code, "Transaction is " + status + ", expected " + expected);
    }

    private void requireNotFinal() {
        if (isFinal()) throw new ApiException(409, "TRANSACTION_ALREADY_FINAL", "Transaction is already " + status);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
