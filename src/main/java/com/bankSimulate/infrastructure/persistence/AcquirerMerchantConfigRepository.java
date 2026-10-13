package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.acquirer.AcquirerMerchantConfig;
import com.bankSimulate.domain.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AcquirerMerchantConfigRepository extends JpaRepository<AcquirerMerchantConfig, UUID> {
    boolean existsByAcquirerIdAndMerchantId(UUID acquirerId, UUID merchantId);
    List<AcquirerMerchantConfig> findAllByMerchantId(UUID merchantId);
    boolean existsByAcquirerIdAndMerchantIdAndStatus(UUID acquirerId, UUID merchantId, Status status);
}
