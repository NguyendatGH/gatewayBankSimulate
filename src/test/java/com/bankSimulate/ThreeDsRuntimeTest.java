package com.bankSimulate;

import com.bankSimulate.application.GatewayRuntimeAuth;
import com.bankSimulate.application.BankService;
import com.bankSimulate.application.GatewayRuntimeService;
import com.bankSimulate.domain.ConfigEnums.Channel;
import com.bankSimulate.domain.ConfigEnums.PaymentMethod;
import com.bankSimulate.domain.ConfigEnums.ThreeDsPolicy;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.merchant.Merchant;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreeDsRuntimeTest {

    @Test
    void requiredPolicyShowsChallengeAndRejectsNon3dsSuccess() {
        Fixture fixture = fixture(ThreeDsPolicy.REQUIRED, PaymentMethod.CARD);
        var created = fixture.service.createPayment(fixture.access, request("CARD", null));

        assertTrue(created.threeDsRequired());
        assertFalse(created.threeDsChoiceRequired());
        assertTrue(fixture.service.checkoutPage(created.providerPaymentId()).contains("Authenticate 3DS and pay"));

        ApiException bypass = assertThrows(ApiException.class,
                () -> fixture.service.completePayment(created.providerPaymentId(), true, false, false));
        assertEquals("THREEDS_REQUIRED", bypass.getCode());

        fixture.service.completePayment(created.providerPaymentId(), true, false, true);
        var status = fixture.service.paymentStatus(fixture.access, created.providerPaymentId());
        assertEquals("PAID", status.status());
        assertTrue(status.threeDsAuthenticated());
        assertEquals("AUTHENTICATED", status.threeDsStatus());
    }

    @Test
    void optionalPolicyLetsBuyerChooseNon3ds() {
        Fixture fixture = fixture(ThreeDsPolicy.OPTIONAL, PaymentMethod.CARD);
        var created = fixture.service.createPayment(fixture.access, request("CARD", null));

        assertFalse(created.threeDsRequired());
        assertTrue(created.threeDsChoiceRequired());
        String page = fixture.service.checkoutPage(created.providerPaymentId());
        assertTrue(page.contains("Pay with 3DS"));
        assertTrue(page.contains("Pay without 3DS"));

        fixture.service.completePayment(created.providerPaymentId(), true, false, false);
        var status = fixture.service.paymentStatus(fixture.access, created.providerPaymentId());
        assertEquals("PAID", status.status());
        assertFalse(status.threeDsAuthenticated());
        assertEquals("NOT_REQUIRED", status.threeDsStatus());
    }

    @Test
    void nonCardPaymentCannotRequest3ds() {
        Fixture fixture = fixture(ThreeDsPolicy.DISABLED, PaymentMethod.PAYNOW);
        ApiException error = assertThrows(ApiException.class,
                () -> fixture.service.createPayment(fixture.access, request("PAYNOW", true)));
        assertEquals("THREEDS_CARD_ONLY", error.getCode());
    }

    @Test
    void qrPaymentReturnsMockPayloadAndUsesNon3dsFlow() {
        Fixture fixture = fixture(ThreeDsPolicy.DISABLED, PaymentMethod.QR);
        var created = fixture.service.createPayment(fixture.access, request("QR", null));

        assertNotNull(created.qrCode());
        assertTrue(created.qrCode().contains("MOCKQR|"));
        assertFalse(created.threeDsRequired());
        assertTrue(fixture.service.checkoutPage(created.providerPaymentId()).contains("QR / VietQR"));

        fixture.service.completePayment(created.providerPaymentId(), true, false, false);
        assertEquals("PAID", fixture.service.paymentStatus(fixture.access, created.providerPaymentId()).status());
    }

    @Test
    void failedCheckoutCanBeRetriedOnTheSamePaymentLink() {
        Fixture fixture = fixture(ThreeDsPolicy.REQUIRED, PaymentMethod.CARD);
        var created = fixture.service.createPayment(fixture.access, request("CARD", null));

        fixture.service.completePayment(created.providerPaymentId(), false, false, true);
        assertEquals("CANCELLED", fixture.service.paymentStatus(fixture.access, created.providerPaymentId()).status());

        fixture.service.completePayment(created.providerPaymentId(), true, false, true);
        var status = fixture.service.paymentStatus(fixture.access, created.providerPaymentId());
        assertEquals("PAID", status.status());
        assertEquals("AUTHENTICATED", status.threeDsStatus());
    }

    private static GatewayRuntimeDtos.CreatePaymentRequest request(String method, Boolean threeDs) {
        return new GatewayRuntimeDtos.CreatePaymentRequest(100001, 120000, "Test payment", method, threeDs,
                List.of(new GatewayRuntimeDtos.Item("Ticket", 1, 120000)),
                "http://localhost/return", "http://localhost/cancel", null);
    }

    private static Fixture fixture(ThreeDsPolicy policy, PaymentMethod method) {
        Merchant merchant = new Merchant("MER000001", "Test merchant", "http://127.0.0.1:1/webhook");
        Terminal terminal = new Terminal("GT000001", merchant.getId(), "Web terminal", Channel.WEB, "VND", policy, null);
        TerminalPaymentMethodRepository methods = mock(TerminalPaymentMethodRepository.class);
        when(methods.existsByTerminalIdAndPaymentMethod(terminal.getId(), method)).thenReturn(true);
        GatewayRuntimeService service = new GatewayRuntimeService(mock(GatewayRuntimeAuth.class), mock(BankService.class), methods,
                mock(RoutingProfileRepository.class), mock(RoutingRuleRepository.class), mock(AcquirerRepository.class),
                mock(AcquirerMerchantConfigRepository.class),
                new ObjectMapper(), "http://localhost:8090", 100000000);
        return new Fixture(service, new GatewayRuntimeAuth.Access(merchant, terminal, "secret"));
    }

    private record Fixture(GatewayRuntimeService service, GatewayRuntimeAuth.Access access) {}
}
