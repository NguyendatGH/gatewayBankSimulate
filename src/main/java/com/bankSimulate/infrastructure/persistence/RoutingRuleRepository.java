package com.bankSimulate.infrastructure.persistence;

import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.routing.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {
    List<RoutingRule> findAllByRoutingProfileIdOrderByPaymentMethodAscPriorityAsc(UUID profileId);

    boolean existsByRoutingProfileIdAndPaymentMethodAndPriority(UUID profileId, PaymentMethod method, int priority);

    boolean existsByRoutingProfileIdAndPaymentMethodAndEnabledTrue(UUID profileId, PaymentMethod method);

    List<RoutingRule> findAllByRoutingProfileIdAndPaymentMethodAndEnabledTrueOrderByPriorityAsc(UUID profileId, PaymentMethod method);
}
