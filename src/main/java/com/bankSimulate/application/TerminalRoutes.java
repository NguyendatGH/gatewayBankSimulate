package com.bankSimulate.application;

import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.routing.RoutingProfile;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.AcquirerRepository;
import com.bankSimulate.infrastructure.persistence.RoutingProfileRepository;
import com.bankSimulate.infrastructure.persistence.RoutingRuleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class TerminalRoutes {

    public record Plan(String source, List<Acquirer> acquirers) {
    }

    private final RoutingProfileRepository profiles;
    private final RoutingRuleRepository rules;
    private final AcquirerRepository acquirers;

    public TerminalRoutes(RoutingProfileRepository profiles, RoutingRuleRepository rules, AcquirerRepository acquirers) {
        this.profiles = profiles;
        this.rules = rules;
        this.acquirers = acquirers;
    }

    public Plan plan(Terminal terminal, PaymentMethod method) {
        if (terminal.getAcquirerId() != null) {
            Acquirer bank = acquirers.findById(terminal.getAcquirerId())
                    .orElseThrow(() -> new ApiException(409, "ACQUIRER_NOT_FOUND", "Terminal bank no longer exists"));
            return new Plan("bank " + bank.getCode(), List.of(bank));
        }
        if (terminal.getRoutingProfileId() == null)
            throw new ApiException(409, "ROUTING_NOT_CONFIGURED", "Terminal has no bank and no routing profile");
        RoutingProfile profile = profiles.findById(terminal.getRoutingProfileId())
                .orElseThrow(() -> new ApiException(404, "ROUTING_PROFILE_NOT_FOUND", "Routing profile not found"));
        if (profile.getStatus() != Status.ACTIVE)
            throw new ApiException(409, "ROUTING_PROFILE_INACTIVE", "Routing profile is inactive");
        List<Acquirer> ordered = rules
                .findAllByRoutingProfileIdAndPaymentMethodAndEnabledTrueOrderByPriorityAsc(profile.getId(), method).stream()
                .map(rule -> acquirers.findById(rule.getAcquirerId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return new Plan("profile " + profile.getCode(), ordered);
    }
}
