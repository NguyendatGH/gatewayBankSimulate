package com.bankSimulate.infrastructure.persistence;

import com.bankSimulate.domain.enums.GateWayTransactionStatus;
import com.bankSimulate.domain.gateway.GatewayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayTransactionRepository extends JpaRepository<GatewayTransaction, UUID> {

    Optional<GatewayTransaction> findByGwTxnId(String gwTxnId);

    boolean existsByGwTxnId(String gwTxnId);

    @Query(value = "select nextval('trade_number_seq')", nativeQuery = true)
    long nextTradeNumber();

    default String nextTradeNo() {
        return "TradeNo" + String.format("%06d", nextTradeNumber());
    }

    Optional<GatewayTransaction> findByMerchantIdAndTerminalIdAndOrderCode(UUID merchantId, UUID terminalId, String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GatewayTransaction> findWithLockByBankCodeAndBankRef(String bankCode, String bankRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GatewayTransaction> findWithLockByGwTxnId(String gwTxnId);

    List<GatewayTransaction> findTop50ByStatusInAndExpiresAtBefore(
            List<GateWayTransactionStatus> statuses, Instant before);
}
