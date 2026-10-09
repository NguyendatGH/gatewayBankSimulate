package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.merchant.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByMerNo(String merNo);
    @Query(value = "select nextval('merchant_number_seq')", nativeQuery = true) long nextNumber();
}
