package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.terminal.TerminalPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TerminalPaymentMethodRepository extends JpaRepository<TerminalPaymentMethod, TerminalPaymentMethod.Key> {
    List<TerminalPaymentMethod> findAllByTerminalId(UUID terminalId);
    void deleteAllByTerminalId(UUID terminalId);
    boolean existsByTerminalIdAndPaymentMethod(UUID terminalId, PaymentMethod method);
}
