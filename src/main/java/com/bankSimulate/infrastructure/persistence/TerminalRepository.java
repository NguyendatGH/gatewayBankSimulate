package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.terminal.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
    Optional<Terminal> findByTerminalId(String terminalId);
    List<Terminal> findAllByMerchantId(UUID merchantId);
    long countByMerchantId(UUID merchantId);
    Optional<Terminal> findFirstByMerchantIdAndPurpose(UUID merchantId, com.bankSimulate.domain.enums.TerminalPurpose purpose);
    List<Terminal> findAllByMerchantIdAndPurpose(UUID merchantId, com.bankSimulate.domain.enums.TerminalPurpose purpose);
    @Query(value = "select nextval('terminal_number_seq')", nativeQuery = true) long nextNumber();
}
