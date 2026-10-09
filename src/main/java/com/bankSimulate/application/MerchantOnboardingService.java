package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MerchantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOnboardingService.class);

    private final MerchantService merchants;
    private final AcquirerService acquirers;
    private final TerminalService terminals;
    private final List<String> defaultAcquirers;
    private final String defaultProfile;
    private final List<String> defaultMethods;
    private final String defaultThreeDs;
    private final String defaultChannel;
    private final String defaultCurrency;

    public MerchantOnboardingService(MerchantService merchants, AcquirerService acquirers, TerminalService terminals,
                                     @Value("${gateway.defaults.acquirers:bank-a,bank-b,QR_PROVIDER_A}") List<String> defaultAcquirers,
                                     @Value("${gateway.defaults.terminal.routing-profile:STANDARD}") String defaultProfile,
                                     @Value("${gateway.defaults.terminal.payment-methods:CARD,QR}") List<String> defaultMethods,
                                     @Value("${gateway.defaults.terminal.three-ds-policy:OPTIONAL}") String defaultThreeDs,
                                     @Value("${gateway.defaults.terminal.channel:WEB}") String defaultChannel,
                                     @Value("${gateway.defaults.terminal.currency:VND}") String defaultCurrency) {
        this.merchants = merchants;
        this.acquirers = acquirers;
        this.terminals = terminals;
        this.defaultAcquirers = defaultAcquirers;
        this.defaultProfile = defaultProfile;
        this.defaultMethods = defaultMethods;
        this.defaultThreeDs = defaultThreeDs;
        this.defaultChannel = defaultChannel;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional
    public AdminDtos.OnboardingResponse onboard(AdminDtos.OnboardingRequest request) {
        String channel = blank(request.channel()) ? defaultChannel : request.channel();
        String currency = blank(request.currency()) ? defaultCurrency : request.currency();

        AdminDtos.MerchantCreatedResponse merchant = merchants.create(
                new AdminDtos.CreateMerchantRequest(request.name(), request.webhookUrl()), request.externalReference());

        if (request.settlementAccount() != null)
            merchants.updateSettlement(merchant.merNo(), request.settlementAccount());

        for (String code : defaultAcquirers) {
            String suffix = merchant.merNo();
            acquirers.configure(merchant.merNo(),
                    new AdminDtos.AcquirerConfigRequest(code, "MID-" + code + "-" + suffix, "TID-" + code + "-" + suffix));
        }

        AdminDtos.TerminalResponse terminal = terminals.create(merchant.merNo(),
                new AdminDtos.CreateTerminalRequest(request.name() + " " + channel, channel, currency,
                        defaultMethods, defaultThreeDs, defaultProfile));
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.merNo(), terminal.terminalId(), null)) {
            log.info("Merchant onboarded externalReference={} acquirers={} channel={} methods={} routing={}",
                    request.externalReference(), defaultAcquirers, channel, defaultMethods, defaultProfile);
        }

        return new AdminDtos.OnboardingResponse(merchant.merNo(), terminal.terminalId(), merchant.merchantSecret(),
                terminal.routingProfileCode(), terminal.paymentMethods(), terminal.threeDsPolicy());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
