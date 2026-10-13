package com.bankSimulate.application;

import com.bankSimulate.domain.enums.*;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.routing.*;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoutingService {
    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private final RoutingProfileRepository profiles;
    private final RoutingRuleRepository rules;
    private final AcquirerService acquirers;

    public RoutingService(RoutingProfileRepository profiles, RoutingRuleRepository rules, AcquirerService acquirers) {
        this.profiles = profiles;
        this.rules = rules;
        this.acquirers = acquirers;
    }

    @Transactional
    public AdminDtos.RoutingProfileResponse create(AdminDtos.CreateRoutingProfileRequest request) {
        if (profiles.findByCode(request.code()).isPresent())
            throw new ApiException(409, "ROUTING_PROFILE_EXISTS", "Routing profile already exists");
        RoutingProfile p = profiles.save(new RoutingProfile(request.code().trim(), request.name().trim()));
        return response(p);
    }

    @Transactional
    public AdminDtos.RoutingProfileResponse addRule(String code, AdminDtos.RoutingRuleRequest request) {
        RoutingProfile p = require(code);
        PaymentMethod method = ConfigParsers.enumValue(PaymentMethod.class, request.paymentMethod(), "PAYMENT_METHOD_NOT_SUPPORTED");
        if (request.priority() < 1) throw new ApiException(400, "INVALID_PRIORITY", "Priority must be at least 1");
        var acquirer = acquirers.require(request.acquirerCode());
        if (acquirer.getStatus() != Status.ACTIVE)
            throw new ApiException(409, "ACQUIRER_INACTIVE", "Acquirer is inactive");
        if (!acquirer.getPaymentMethods().contains(method))
            throw new ApiException(409, "ACQUIRER_METHOD_NOT_SUPPORTED", "Acquirer " + acquirer.getCode()
                    + " does not accept " + method + " (accepts " + acquirer.getPaymentMethods()
                    + "). Add " + method + " to the acquirer or pick another one");
        if (rules.existsByRoutingProfileIdAndPaymentMethodAndPriority(p.getId(), method, request.priority()))
            throw new ApiException(409, "ROUTING_PRIORITY_EXISTS", "Priority already exists for payment method");
        rules.save(new RoutingRule(p.getId(), method, acquirer.getId(), request.priority()));
        log.info("Routing rule added: profile={} paymentMethod={} acquirer={} priority={}",
                code, method, acquirer.getCode(), request.priority());
        return response(p);
    }

    @Transactional(readOnly = true)
    public AdminDtos.RoutingProfileResponse get(String code) {
        return response(require(code));
    }

    public RoutingProfile require(String code) {
        return profiles.findByCode(code).orElseThrow(() -> new ApiException(404, "ROUTING_PROFILE_NOT_FOUND", "Routing profile not found"));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RoutingProfileListItem> list() {
        return profiles.findAll().stream()
                .sorted(java.util.Comparator.comparing(RoutingProfile::getCode))
                .map(p -> new AdminDtos.RoutingProfileListItem(p.getCode(), p.getName(), p.getStatus().name()))
                .toList();
    }

    private AdminDtos.RoutingProfileResponse response(RoutingProfile p) {
        Map<String, List<AdminDtos.RouteResponse>> routes = new LinkedHashMap<>();
        for (RoutingRule rule : rules.findAllByRoutingProfileIdOrderByPaymentMethodAscPriorityAsc(p.getId()))
            acquirers.requireById(rule.getAcquirerId()).ifPresent(a -> routes.computeIfAbsent(rule.getPaymentMethod().name(), ignored -> new ArrayList<>()).add(new AdminDtos.RouteResponse(rule.getPriority(), a.getCode())));
        return new AdminDtos.RoutingProfileResponse(p.getCode(), p.getStatus().name(), routes);
    }
}
