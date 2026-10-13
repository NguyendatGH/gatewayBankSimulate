package com.bankSimulate.application;

import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.acquirer.AcquirerMerchantConfig;
import com.bankSimulate.domain.enums.*;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.routing.RoutingProfile;
import com.bankSimulate.domain.terminal.*;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TerminalService {
    private static final Logger log = LoggerFactory.getLogger(TerminalService.class);
    private final TerminalRepository terminals;
    private final TerminalPaymentMethodRepository methods;
    private final MerchantService merchantService;
    private final RoutingProfileRepository profiles;
    private final RoutingRuleRepository rules;
    private final AcquirerRepository acquirers;
    private final AcquirerMerchantConfigRepository acquirerConfigs;
    private final TerminalRoutes routes;
    private final GatewayMoneyService money;

    public TerminalService(TerminalRepository terminals, TerminalPaymentMethodRepository methods, MerchantService merchantService,
                           RoutingProfileRepository profiles, RoutingRuleRepository rules,
                           AcquirerRepository acquirers, AcquirerMerchantConfigRepository acquirerConfigs,
                           TerminalRoutes routes, GatewayMoneyService money) {
        this.money = money;
        this.terminals = terminals;
        this.methods = methods;
        this.merchantService = merchantService;
        this.profiles = profiles;
        this.rules = rules;
        this.acquirers = acquirers;
        this.acquirerConfigs = acquirerConfigs;
        this.routes = routes;
    }

    @Transactional
    public AdminDtos.TerminalResponse create(String merNo, AdminDtos.CreateTerminalRequest request) {
        Merchant merchant = activeMerchant(merNo);
        EnumSet<PaymentMethod> paymentMethods = ConfigParsers.methods(request.paymentMethods());
        Channel channel = ConfigParsers.enumValue(Channel.class, request.channel(), "INVALID_CHANNEL");
        String currency = ConfigParsers.currency(request.currency());
        ThreeDsPolicy policy = request.threeDsPolicy() == null ? null : ConfigParsers.enumValue(ThreeDsPolicy.class, request.threeDsPolicy(), "INVALID_3DS_POLICY");
        ConfigParsers.cardPolicy(paymentMethods, policy);
        Acquirer bank = bank(request.acquirerCode());
        RoutingProfile profile = bank == null ? profile(request.routingProfileCode()) : null;
        Terminal terminal = terminals.save(new Terminal("TerNo" + String.format("%06d", terminals.nextNumber()), merchant.getId(), request.name().trim(), channel, currency, policy, profile == null ? null : profile.getId()));
        if (bank != null) {
            ensureConnection(merchant, bank);
            terminal.useAcquirer(bank.getId());
        }
        if (request.settlementAccount() != null) {
            ConfigParsers.Settlement s = ConfigParsers.settlement(request.settlementAccount());
            terminal.changeSettlementAccount(s.bankBin(), s.accountNumber(), s.accountName());
        }
        methods.saveAll(paymentMethods.stream().map(m -> new TerminalPaymentMethod(terminal.getId(), m)).toList());
        validateActive(terminal, paymentMethods);
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merNo, terminal.getTerminalId(), null)) {
            log.info("Terminal created bank={} settlement={}", bank == null ? null : bank.getCode(),
                    terminal.hasSettlementAccount() ? ConfigParsers.maskAccount(terminal.getSettlementAccountNumber()) : "merchant");
        }
        return response(terminal, merchant, paymentMethods);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.TerminalResponse> list(String merNo) {
        Merchant m = merchantService.require(merNo);
        return terminals.findAllByMerchantId(m.getId()).stream().map(t -> response(t, m, enabled(t))).toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.TerminalResponse get(String terminalId) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        return response(t, m, enabled(t));
    }

    @Transactional
    public AdminDtos.TerminalResponse update(String terminalId, AdminDtos.UpdateTerminalRequest request) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        String name = request.name();
        if (name != null && name.isBlank())
            throw new ApiException(400, "INVALID_TERMINAL_NAME", "Terminal name is required");
        Channel channel = request.channel() == null ? null : ConfigParsers.enumValue(Channel.class, request.channel(), "INVALID_CHANNEL");
        String currency = request.currency() == null ? null : ConfigParsers.currency(request.currency());
        Status status = request.status() == null ? null : ConfigParsers.enumValue(Status.class, request.status(), "INVALID_STATUS");
        if (status == Status.INACTIVE && t.getStatus() == Status.ACTIVE) requireNotInUse(t, m);
        t.update(name == null ? null : name.trim(), channel, currency, status);
        if (t.getStatus() == Status.ACTIVE) validateActive(t, enabled(t));
        return response(t, m, enabled(t));
    }

    @Transactional
    public AdminDtos.TerminalResponse updateMethods(String terminalId, AdminDtos.PaymentMethodsRequest request) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        EnumSet<PaymentMethod> values = ConfigParsers.methods(request.paymentMethods());
        ConfigParsers.cardPolicy(values, t.getThreeDsPolicy());
        methods.deleteAllByTerminalId(t.getId());
        methods.flush();
        methods.saveAll(values.stream().map(v -> new TerminalPaymentMethod(t.getId(), v)).toList());
        if (t.getStatus() == Status.ACTIVE) validateActive(t, values);
        return response(t, m, values);
    }

    @Transactional
    public AdminDtos.TerminalResponse update3ds(String terminalId, AdminDtos.ThreeDsRequest request) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        ThreeDsPolicy policy = ConfigParsers.enumValue(ThreeDsPolicy.class, request.threeDsPolicy(), "INVALID_3DS_POLICY");
        ConfigParsers.cardPolicy(enabled(t), policy);
        t.setThreeDsPolicy(policy);

        if (t.getStatus() == Status.ACTIVE) validateActive(t, enabled(t));
        return response(t, m, enabled(t));
    }

    @Transactional
    public AdminDtos.TerminalResponse configure(String terminalId, AdminDtos.TerminalConfigurationRequest request) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        EnumSet<PaymentMethod> values = ConfigParsers.methods(request.paymentMethods());
        ThreeDsPolicy policy = request.threeDsPolicy() == null || request.threeDsPolicy().isBlank() ? null
                : ConfigParsers.enumValue(ThreeDsPolicy.class, request.threeDsPolicy(), "INVALID_3DS_POLICY");
        ConfigParsers.cardPolicy(values, policy);
        Acquirer bank = bank(request.acquirerCode());
        RoutingProfile p = bank == null ? profile(request.routingProfileCode()) : null;
        if (bank != null) {
            ensureConnection(m, bank);
            t.useAcquirer(bank.getId());
        } else {
            t.useRoutingProfile(p == null ? null : p.getId());
        }
        methods.deleteAllByTerminalId(t.getId());
        methods.flush();
        methods.saveAll(values.stream().map(v -> new TerminalPaymentMethod(t.getId(), v)).toList());
        t.setThreeDsPolicy(policy);
        if (t.getStatus() == Status.ACTIVE) validateActive(t, values);
        int released = 0;
        if (request.settlementAccount() != null) {
            ConfigParsers.Settlement s = ConfigParsers.settlement(request.settlementAccount());
            t.changeSettlementAccount(s.bankBin(), s.accountNumber(), s.accountName());
            released = money.releaseHeldFunds(m.getId(), t.getId(), s.bankBin(), s.accountNumber());
        }
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(m.getMerNo(), t.getTerminalId(), null)) {
            log.info("Terminal configured methods={} threeDsPolicy={} bank={} routingProfile={} settlement={} releasedHeldPayments={}",
                    values, policy, bank == null ? null : bank.getCode(), p == null ? null : p.getCode(),
                    t.hasSettlementAccount() ? ConfigParsers.maskAccount(t.getSettlementAccountNumber()) : "merchant", released);
        }
        return response(t, m, values);
    }

    @Transactional
    public AdminDtos.TerminalResponse assignRouting(String terminalId, AdminDtos.RoutingProfileRequest request) {
        Terminal t = require(terminalId);
        Merchant m = merchantService.requireById(t.getMerchantId());
        RoutingProfile p = profile(request.routingProfileCode());
        if (p == null) throw new ApiException(400, "ROUTING_PROFILE_REQUIRED", "routingProfileCode is required");
        t.useRoutingProfile(p.getId());
        if (t.getStatus() == Status.ACTIVE) validateActive(t, enabled(t));
        return response(t, m, enabled(t));
    }

    private void requireNotInUse(Terminal t, Merchant m) {
        if (t.isChannel() && !t.isRetired())
            throw new ApiException(409, "TERMINAL_IN_USE",
                    "Terminal này là một kênh nhận tiền ban tổ chức đang mở. Ban tổ chức phải xóa kênh đó trước.");
        boolean hasLiveChannel = terminals.findAllByMerchantId(m.getId()).stream().anyMatch(c -> c.isChannel() && c.isLive());
        if (t.getPurpose() == TerminalPurpose.DEFAULT && !hasLiveChannel)
            throw new ApiException(409, "TERMINAL_IN_USE",
                    "Đây là terminal mặc định, đang nhận đơn vì merchant chưa có kênh nào. Chọn terminal mặc định khác trước rồi mới tắt.");
    }

    @Transactional
    public AdminDtos.TerminalResponse setDefault(String merNo, String terminalId) {
        Merchant m = activeMerchant(merNo);
        Terminal t = require(terminalId);
        if (!t.getMerchantId().equals(m.getId()))
            throw new ApiException(403, "TERMINAL_NOT_OWNED", "Terminal does not belong to merchant");
        if (t.isChannel() && !t.isRetired())
            throw new ApiException(409, "TERMINAL_IS_CHANNEL", "Terminal này là kênh nhận tiền, không đặt làm mặc định được");
        if (t.getStatus() != Status.ACTIVE) throw new ApiException(409, "TERMINAL_INACTIVE", "Terminal is inactive");
        validateActive(t, enabled(t));
        terminals.findFirstByMerchantIdAndPurpose(m.getId(), TerminalPurpose.DEFAULT)
                .filter(old -> !old.getId().equals(t.getId()))
                .ifPresent(old -> {
                    old.makeSpare();
                    terminals.saveAndFlush(old);
                });
        t.makeDefault();
        terminals.saveAndFlush(t);
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merNo, t.getTerminalId(), null)) {
            log.info("Terminal mặc định của merchant đổi sang {}", t.getTerminalId());
        }
        return response(t, m, enabled(t));
    }

    private Merchant activeMerchant(String merNo) {
        Merchant m = merchantService.require(merNo);
        if (m.getStatus() != Status.ACTIVE) throw new ApiException(409, "MERCHANT_INACTIVE", "Merchant is inactive");
        return m;
    }

    @Transactional(readOnly = true)
    public Terminal requireTerminal(String terminalId) {
        return require(terminalId);
    }

    private Terminal require(String id) {
        return terminals.findByTerminalId(id).orElseThrow(() -> new ApiException(404, "TERMINAL_NOT_FOUND", "Terminal " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Terminal requireById(java.util.UUID id) {
        return terminals.findById(id).orElseThrow(() -> new ApiException(404, "TERMINAL_NOT_FOUND", "Terminal not found"));
    }

    private RoutingProfile profile(String code) {
        if (code == null || code.isBlank()) return null;
        RoutingProfile p = profiles.findByCode(code).orElseThrow(() -> new ApiException(404, "ROUTING_PROFILE_NOT_FOUND", "Routing profile not found"));
        if (p.getStatus() != Status.ACTIVE)
            throw new ApiException(409, "ROUTING_PROFILE_INACTIVE", "Routing profile is inactive");
        return p;
    }

    private Acquirer bank(String code) {
        if (code == null || code.isBlank()) return null;
        Acquirer bank = acquirers.findByCode(code.trim())
                .orElseThrow(() -> new ApiException(404, "ACQUIRER_NOT_FOUND", "Bank " + code + " not found"));
        if (bank.getStatus() != Status.ACTIVE) throw new ApiException(409, "ACQUIRER_INACTIVE", "Bank " + code + " is inactive");
        return bank;
    }

    private void ensureConnection(Merchant merchant, Acquirer bank) {
        if (acquirerConfigs.existsByAcquirerIdAndMerchantId(bank.getId(), merchant.getId())) return;
        String suffix = merchant.getMerNo() + "-" + bank.getCode();
        acquirerConfigs.save(new AcquirerMerchantConfig(bank.getId(), merchant.getId(), "MID-" + suffix, "TID-" + suffix));
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.getMerNo(), null, null)) {
            log.info("Acquirer connection created for chosen bank {}", bank.getCode());
        }
    }

    private EnumSet<PaymentMethod> enabled(Terminal t) {
        return methods.findAllByTerminalId(t.getId()).stream().map(TerminalPaymentMethod::getPaymentMethod).collect(Collectors.toCollection(() -> EnumSet.noneOf(PaymentMethod.class)));
    }

    private void validateActive(Terminal t, EnumSet<PaymentMethod> values) {
        Merchant merchant = merchantService.requireById(t.getMerchantId());
        for (PaymentMethod method : values) {
            TerminalRoutes.Plan plan = routes.plan(t, method);
            RouteCheck check = checkRoute(merchant, plan.acquirers(), method, t.getThreeDsPolicy());
            if (!check.hasRule())
                throw new ApiException(409, "ROUTING_NOT_CONFIGURED", "Routing " + plan.source()
                        + " has no route for " + method + ". Add a " + method + " rule to the profile or pick another profile");
            if (check.usable()) continue;
            if (!check.notConnected().isEmpty())
                throw new ApiException(409, "ACQUIRER_NOT_CONFIGURED", "Merchant " + merchant.getMerNo()
                        + " has no acquirer connection for the " + method + " route. Connect it to one of "
                        + check.notConnected() + " (Merchant Detail > Acquirer Connections)");
            if (!check.noThreeDs().isEmpty())
                throw new ApiException(409, "ACQUIRER_3DS_NOT_SUPPORTED", "Terminal requires 3DS but the " + method
                        + " route of " + plan.source() + " only reaches " + check.noThreeDs()
                        + ", which do not support 3DS. Pick a bank or profile with 3DS, or set 3DS policy to OPTIONAL");
            throw new ApiException(409, "ACQUIRER_METHOD_NOT_SUPPORTED", method + " route of " + plan.source()
                    + " points to " + check.notAccepting() + ", which are inactive or do not accept " + method);
        }
    }

    private record RouteCheck(boolean hasRule, boolean usable, List<String> notConnected, List<String> noThreeDs,
                              List<String> notAccepting) {
    }

    private RouteCheck checkRoute(Merchant merchant, List<Acquirer> candidates, PaymentMethod method, ThreeDsPolicy policy) {
        List<String> notConnected = new ArrayList<>();
        List<String> noThreeDs = new ArrayList<>();
        List<String> notAccepting = new ArrayList<>();
        for (Acquirer a : candidates) {
            if (!a.accepts(method)) {
                notAccepting.add(a.getCode());
            } else if (!a.meetsThreeDs(method, policy)) {
                noThreeDs.add(a.getCode());
            } else if (!acquirerConfigs.existsByAcquirerIdAndMerchantIdAndStatus(a.getId(), merchant.getId(), Status.ACTIVE)) {
                notConnected.add(a.getCode());
            } else {
                return new RouteCheck(true, true, notConnected, noThreeDs, notAccepting);
            }
        }
        return new RouteCheck(!candidates.isEmpty(), false, notConnected, noThreeDs, notAccepting);
    }

    @Transactional(readOnly = true)
    public EnumSet<PaymentMethod> routableMethods(Terminal t, Merchant m) {
        EnumSet<PaymentMethod> routable = EnumSet.noneOf(PaymentMethod.class);
        if (t.getStatus() != Status.ACTIVE || m.getStatus() != Status.ACTIVE) return routable;
        for (PaymentMethod method : enabled(t)) {
            try {
                if (checkRoute(m, routes.plan(t, method).acquirers(), method, t.getThreeDsPolicy()).usable())
                    routable.add(method);
            } catch (ApiException notRoutable) {
                return routable;
            }
        }
        return routable;
    }

    private AdminDtos.TerminalResponse response(Terminal t, Merchant m, EnumSet<PaymentMethod> values) {
        RoutingProfile p = t.getRoutingProfileId() == null ? null : profiles.findById(t.getRoutingProfileId()).orElse(null);
        AdminDtos.RoutingProfileSummary summary = p == null ? null
                : new AdminDtos.RoutingProfileSummary(p.getCode(), p.getName(), p.getStatus().name(), routesOf(p));
        return new AdminDtos.TerminalResponse(t.getTerminalId(), m.getMerNo(), t.getName(), t.getChannel().name(),
                t.getCurrency(), values.stream().map(Enum::name).toList(),
                t.getThreeDsPolicy() == null ? null : t.getThreeDsPolicy().name(),
                p == null ? null : p.getCode(), t.getStatus().name(), summary,
                routableMethods(t, m).stream().map(Enum::name).toList(),
                t.getAcquirerId() == null ? null : acquirers.findById(t.getAcquirerId()).map(Acquirer::getCode).orElse(null),
                t.hasSettlementAccount() ? new AdminDtos.SettlementAccountResponse(t.getSettlementBankBin(),
                        ConfigParsers.maskAccount(t.getSettlementAccountNumber()), t.getSettlementAccountName()) : null,
                t.getPurpose().name(), t.isRetired());
    }

    private java.util.Map<String, List<AdminDtos.RouteResponse>> routesOf(RoutingProfile p) {
        java.util.Map<String, List<AdminDtos.RouteResponse>> out = new java.util.LinkedHashMap<>();
        for (var rule : rules.findAllByRoutingProfileIdOrderByPaymentMethodAscPriorityAsc(p.getId()))
            acquirers.findById(rule.getAcquirerId()).ifPresent(a -> out
                    .computeIfAbsent(rule.getPaymentMethod().name(), ignored -> new java.util.ArrayList<>())
                    .add(new AdminDtos.RouteResponse(rule.getPriority(), a.getCode())));
        return out;
    }

}
