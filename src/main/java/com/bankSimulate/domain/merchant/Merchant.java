package com.bankSimulate.domain.merchant;

import com.bankSimulate.domain.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;
    @Column(name = "mer_no", nullable = false, unique = true)
    private String merNo;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "webhook_url")
    private String webhookUrl;
    @Column(name = "external_reference", length = 128)
    private String externalReference;
    @Column(name = "settlement_bank_bin", length = 6)
    private String settlementBankBin;
    @Column(name = "settlement_account_number", length = 30)
    private String settlementAccountNumber;
    @Column(name = "settlement_account_name", length = 120)
    private String settlementAccountName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {
    }

    public Merchant(String merNo, String name, String webhookUrl) {
        this(merNo, name, webhookUrl, null);
    }

    public Merchant(String merNo, String name, String webhookUrl, String externalReference) {
        this.id = UUID.randomUUID();
        this.merNo = merNo;
        this.name = name;
        this.webhookUrl = webhookUrl;
        this.externalReference = externalReference;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void changeSettlementAccount(String bankBin, String accountNumber, String accountName) {
        this.settlementBankBin = bankBin;
        this.settlementAccountNumber = accountNumber;
        this.settlementAccountName = accountName;
        this.updatedAt = Instant.now();
    }

    public boolean hasSettlementAccount() {
        return settlementBankBin != null && settlementAccountNumber != null;
    }

    public void update(String name, String webhookUrl, Status status) {
        if (name != null) this.name = name;
        if (webhookUrl != null) this.webhookUrl = webhookUrl;
        if (status != null) this.status = status;
        this.updatedAt = Instant.now();
    }

}
