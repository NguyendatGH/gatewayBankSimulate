package com.bankSimulate;

import com.bankSimulate.application.BankService;
import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.application.GatewayRuntimeService;
import com.bankSimulate.config.BankProfileConfiguration;
import com.bankSimulate.domain.ConfigEnums.Channel;
import com.bankSimulate.domain.ConfigEnums.PaymentMethod;
import com.bankSimulate.domain.ConfigEnums.ThreeDsPolicy;
import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.routing.RoutingProfile;
import com.bankSimulate.domain.routing.RoutingRule;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.web.GatewayRuntimeDtos;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankRoutingRuntimeTest {
    @Test
    void cardPaymentUsesTheRoutedBankBean() {
        Merchant merchant = new Merchant("MER000001", "Test merchant", "http://127.0.0.1:1/webhook");
        Acquirer acquirer = new Acquirer("ACQUIRER_A", "Mock A", com.bankSimulate.domain.ConfigEnums.AcquirerType.CARD_ACQUIRER);
        RoutingProfile profile = new RoutingProfile("VN_WEB_DEFAULT", "Vietnam Web Default");
        RoutingRule rule = new RoutingRule(profile.getId(), PaymentMethod.CARD, acquirer.getId(), 1);
        Terminal terminal = new Terminal("GT000001", merchant.getId(), "Web terminal", Channel.WEB, "VND", ThreeDsPolicy.REQUIRED, profile.getId());

        TerminalPaymentMethodRepository methods = mock(TerminalPaymentMethodRepository.class);
        RoutingProfileRepository profiles = mock(RoutingProfileRepository.class);
        RoutingRuleRepository rules = mock(RoutingRuleRepository.class);
        AcquirerRepository acquirers = mock(AcquirerRepository.class);
        AcquirerMerchantConfigRepository configs = mock(AcquirerMerchantConfigRepository.class);
        when(methods.existsByTerminalIdAndPaymentMethod(terminal.getId(), PaymentMethod.CARD)).thenReturn(true);
        when(profiles.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(rules.findAllByRoutingProfileIdOrderByPaymentMethodAscPriorityAsc(profile.getId())).thenReturn(List.of(rule));
        when(acquirers.findById(acquirer.getId())).thenReturn(Optional.of(acquirer));
        when(configs.existsByAcquirerIdAndMerchantIdAndStatus(acquirer.getId(), merchant.getId(), com.bankSimulate.domain.ConfigEnums.Status.ACTIVE)).thenReturn(true);

        BankProfileConfiguration bankProfiles = new BankProfileConfiguration();
        BankService banks = new BankService(List.of(bankProfiles.acquirerABank(), bankProfiles.acquirerBBank(), bankProfiles.qrProviderABank()));
        GatewayRuntimeService service = new GatewayRuntimeService(mock(GatewayRuntimeAuth.class), banks, methods, profiles, rules,
                acquirers, configs, new ObjectMapper(), "http://localhost:8090", 100000000);

        var created = service.createPayment(new GatewayRuntimeAuth.Access(merchant, terminal, "secret"),
                new GatewayRuntimeDtos.CreatePaymentRequest(100001, 120000, "Test payment", "CARD", null,
                        List.of(new GatewayRuntimeDtos.Item("Ticket", 1, 120000)),
                        "http://localhost/return", "http://localhost/cancel", null));

        assertNull(created.qrCode());
        service.completePayment(created.providerPaymentId(), true, false, true);
        var status = service.paymentStatus(new GatewayRuntimeAuth.Access(merchant, terminal, "secret"), created.providerPaymentId());
        assertEquals("PAID", status.status());
        assertTrue(status.transactionRef().startsWith("BS"));
    }
}
