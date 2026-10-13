package com.bankSimulate.domain.routing;

import com.bankSimulate.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "routing_rules", uniqueConstraints = @UniqueConstraint(columnNames = {"routing_profile_id", "payment_method", "priority"}))
public class RoutingRule {
    @Id
    private UUID id;

    @Column( name = "routing_profile_id", nullable = false)
    private UUID routingProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "acquirer_id", nullable = false)
    private UUID acquirerId;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoutingRule() {
    }

    public RoutingRule(UUID profileId, PaymentMethod method, UUID acquirerId, int priority) {
        this.id = UUID.randomUUID();
        this.routingProfileId = profileId;
        this.paymentMethod = method;
        this.acquirerId = acquirerId;
        this.priority = priority;
        this.enabled = true;
        this.createdAt = Instant.now();
    }
}
