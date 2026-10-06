package com.bankSimulate;

import com.bankSimulate.application.BankService;
import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.application.GatewayRuntimeService;
import com.bankSimulate.config.BankProfileConfiguration;
import com.bankSimulate.domain.ConfigEnums;
import com.bankSimulate.domain.acquirer.Acquirer;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.routing.RoutingProfile;
import com.bankSimulate.domain.routing.RoutingRule;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.AcquirerMerchantConfigRepository;
import com.bankSimulate.infrastructure.persistence.AcquirerRepository;
import com.bankSimulate.infrastructure.persistence.RoutingProfileRepository;
import com.bankSimulate.infrastructure.persistence.RoutingRuleRepository;
import com.bankSimulate.infrastructure.persistence.TerminalPaymentMethodRepository;
import com.bankSimulate.infrastructure.web.GatewayRuntimeDtos;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrRoutingRuntimeTest {
    @Test
    void qrPaymentUsesTheQrProviderBeanAndPayload() {
        Merchant merchant = new Merchant("MER000001", "Test merchant", "http://127.0.0.1:1/webhook");
        Acquirer acquirer = new Acquirer("QR_PROVIDER_A", "Mock QR Provider A", ConfigEnums.AcquirerType.QR_PROVIDER);
        RoutingProfile profile = new RoutingProfile("VN_QR_DEFAULT", "Vietnam QR Default");
        RoutingRule rule = new RoutingRule(profile.getId(), ConfigEnums.PaymentMethod.QR, acquirer.getId(), 1);
        Terminal terminal = new Terminal("GT000001", merchant.getId(), "QR terminal", ConfigEnums.Channel.WEB,
                "VND", ConfigEnums.ThreeDsPolicy.DISABLED, profile.getId());

        TerminalPaymentMethodRepository methods = mock(TerminalPaymentMethodRepository.class);
        RoutingProfileRepository profiles = mock(RoutingProfileRepository.class);
        RoutingRuleRepository rules = mock(RoutingRuleRepository.class);
        AcquirerRepository acquirers = mock(AcquirerRepository.class);
        AcquirerMerchantConfigRepository configs = mock(AcquirerMerchantConfigRepository.class);
        when(methods.existsByTerminalIdAndPaymentMethod(terminal.getId(), ConfigEnums.PaymentMethod.QR)).thenReturn(true);
        when(profiles.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(rules.findAllByRoutingProfileIdOrderByPaymentMethodAscPriorityAsc(profile.getId())).thenReturn(List.of(rule));
        when(acquirers.findById(acquirer.getId())).thenReturn(Optional.of(acquirer));
        when(configs.existsByAcquirerIdAndMerchantIdAndStatus(acquirer.getId(), merchant.getId(), ConfigEnums.Status.ACTIVE)).thenReturn(true);

        BankProfileConfiguration bankProfiles = new BankProfileConfiguration();
        BankService banks = new BankService(List.of(bankProfiles.acquirerABank(), bankProfiles.acquirerBBank(), bankProfiles.qrProviderABank()));
        GatewayRuntimeService service = new GatewayRuntimeService(mock(GatewayRuntimeAuth.class), banks, methods, profiles, rules,
                acquirers, configs, new ObjectMapper(), "http://localhost:8090", 100000000);

        var created = service.createPayment(new GatewayRuntimeAuth.Access(merchant, terminal, "secret"),
                new GatewayRuntimeDtos.CreatePaymentRequest(100002, 800000, "QR payment", "QR", null,
                        List.of(new GatewayRuntimeDtos.Item("Ticket", 1, 800000)),
                        "http://localhost/return", "http://localhost/cancel", null));

        assertEquals("MOCKQR|v=1|merchant=MER000001|order=100002|amount=800000|currency=VND", created.qrCode());
        service.completePayment(created.providerPaymentId(), true, false, false);
        assertEquals("PAID", service.paymentStatus(new GatewayRuntimeAuth.Access(merchant, terminal, "secret"), created.providerPaymentId()).status());
        assertTrue(service.paymentStatus(new GatewayRuntimeAuth.Access(merchant, terminal, "secret"), created.providerPaymentId()).transactionRef().startsWith("BS"));
    }
}
