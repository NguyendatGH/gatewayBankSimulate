package com.bankSimulate.domain.acquirer;

import com.bankSimulate.domain.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "acquirer_merchant_configs", uniqueConstraints = @UniqueConstraint(columnNames = {"acquirer_id", "merchant_id"}))
public class AcquirerMerchantConfig {

    @Id
    private UUID id;

    @Column(name = "acquirer_id", nullable = false)
    private UUID acquirerId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "acquirer_mid")
    private String mid;
    @Column(name = "acquirer_tid")
    private String tid;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcquirerMerchantConfig() {
    }

    public AcquirerMerchantConfig(UUID acquirerId, UUID merchantId, String mid, String tid) {
        this.id = UUID.randomUUID();
        this.acquirerId = acquirerId;
        this.merchantId = merchantId;
        this.mid = mid;
        this.tid = tid;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
}
