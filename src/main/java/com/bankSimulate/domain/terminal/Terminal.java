package com.bankSimulate.domain.terminal;
import com.bankSimulate.domain.enums.Channel;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.enums.TerminalPurpose;
import com.bankSimulate.domain.enums.ThreeDsPolicy;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "terminals")
public class Terminal {
    @Id
    private UUID id;

    @Column(name = "terminal_id", nullable = false, unique = true)
    private String terminalId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "three_ds_policy")
    private ThreeDsPolicy threeDsPolicy;

    @Column(name = "routing_profile_id")
    private UUID routingProfileId;
    @Column(name = "acquirer_id")
    private UUID acquirerId;

    @Column(name = "settlement_bank_bin", length = 6)
    private String settlementBankBin;

    @Column(name = "settlement_account_number", length = 30)
    private String settlementAccountNumber;

    @Column(name = "settlement_account_name", length = 120)
    private String settlementAccountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TerminalPurpose purpose;

    @Column(name = "channel_opened_at")
    private Instant channelOpenedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Terminal() {
    }

    public Terminal(String terminalId, UUID merchantId, String name, Channel channel, String currency,
                    ThreeDsPolicy threeDsPolicy, UUID routingProfileId) {
        this.id = UUID.randomUUID();
        this.terminalId = terminalId;
        this.merchantId = merchantId;
        this.name = name;
        this.channel = channel;
        this.currency = currency;
        this.threeDsPolicy = threeDsPolicy;
        this.routingProfileId = routingProfileId;
        this.status = Status.ACTIVE;
        this.purpose = TerminalPurpose.SPARE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String name, Channel channel, String currency, Status status) {
        if (name != null) this.name = name;
        if (channel != null) this.channel = channel;
        if (currency != null) this.currency = currency;
        if (status != null) this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setThreeDsPolicy(ThreeDsPolicy policy) {
        this.threeDsPolicy = policy;
        this.updatedAt = Instant.now();
    }

    public void useRoutingProfile(UUID routingProfileId) {
        this.routingProfileId = routingProfileId;
        this.acquirerId = null;
        this.updatedAt = Instant.now();
    }

    public void useAcquirer(UUID acquirerId) {
        this.acquirerId = acquirerId;
        this.routingProfileId = null;
        this.updatedAt = Instant.now();
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


    public boolean isChannel() {
        return purpose == TerminalPurpose.CHANNEL;
    }

    public boolean isRetired() {
        return retiredAt != null;
    }

    public boolean isLive() {
        return status == Status.ACTIVE && retiredAt == null;
    }

    public void makeDefault() {
        this.purpose = TerminalPurpose.DEFAULT;
        this.channelOpenedAt = null;
        this.retiredAt = null;
        this.updatedAt = Instant.now();
    }

    public void makeSpare() {
        this.purpose = TerminalPurpose.SPARE;
        this.updatedAt = Instant.now();
    }

    public void openAsChannel(Instant openedAt) {
        this.purpose = TerminalPurpose.CHANNEL;
        this.channelOpenedAt = openedAt;
        this.retiredAt = null;
        this.updatedAt = Instant.now();
    }

    public void retire() {
        this.retiredAt = Instant.now();
        this.updatedAt = retiredAt;
    }
}
