package com.bankSimulate.infrastructure.persistence;

import com.bankSimulate.domain.gateway.GatewayTransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GatewayTransactionEventRepository extends JpaRepository<GatewayTransactionEvent, UUID> {
    List<GatewayTransactionEvent> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
