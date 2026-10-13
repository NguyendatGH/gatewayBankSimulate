package com.bankSimulate.infrastructure.persistence;
import com.bankSimulate.domain.acquirer.Acquirer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AcquirerRepository extends JpaRepository<Acquirer, UUID> { Optional<Acquirer> findByCode(String code); }
