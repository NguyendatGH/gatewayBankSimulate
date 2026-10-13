package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.enums.CredentialStatus;
import com.bankSimulate.domain.merchant.MerchantCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MerchantCredentialRepository extends JpaRepository<MerchantCredential, UUID> {
    Optional<MerchantCredential> findFirstByMerchantIdAndStatus(UUID merchantId, CredentialStatus status);
    Optional<MerchantCredential> findFirstByMerchantIdOrderByCredentialVersionDesc(UUID merchantId);
}
