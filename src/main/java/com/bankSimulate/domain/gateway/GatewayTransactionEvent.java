package com.bankSimulate.domain.gateway;


import com.bankSimulate.domain.enums.Actor;
import com.bankSimulate.domain.enums.GateWayTransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "gateway_transaction_events")
public class GatewayTransactionEvent {
    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Actor source;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GatewayTransactionEvent() {
    }

    public GatewayTransactionEvent(UUID transactionId, GateWayTransactionStatus from,
                                   GateWayTransactionStatus to, Actor source, String detail) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.source = source;
        this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
        this.createdAt = Instant.now();
    }
}
