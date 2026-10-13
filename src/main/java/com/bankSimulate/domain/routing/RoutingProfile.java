package com.bankSimulate.domain.routing;

import com.bankSimulate.domain.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "routing_profiles")
public class RoutingProfile {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoutingProfile() {
    }

    public RoutingProfile(String code, String name) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
}
