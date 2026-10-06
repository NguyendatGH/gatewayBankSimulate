package com.bankSimulate;

import com.bankSimulate.application.BankService;
import com.bankSimulate.config.BankProfileConfiguration;
import com.bankSimulate.domain.ConfigEnums.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankServiceTest {
    private final BankService service = new BankService(List.of(
            new BankProfileConfiguration().acquirerABank(),
            new BankProfileConfiguration().acquirerBBank(),
            new BankProfileConfiguration().qrProviderABank()));

    @Test
    void exposesSeparateProfilesForConfiguredBanks() {
        assertEquals(List.of("ACQUIRER_A", "ACQUIRER_B", "QR_PROVIDER_A"),
                service.profiles().stream().map(profile -> profile.code()).toList());
        assertEquals("970422", service.profile("ACQUIRER_A").bankBin());
        assertEquals("970415", service.profile("ACQUIRER_B").bankBin());
        assertEquals("QR_PROVIDER", service.profile("QR_PROVIDER_A").type().name());
    }

    @Test
    void cardAndQrCapabilitiesAreResolvedFromBankProfile() {
        var card = service.authorize("ACQUIRER_A", 1001, 800000, PaymentMethod.CARD, true);
        assertTrue(card.approved());
        assertNotNull(card.transactionRef());

        String qr = service.qrPayload("QR_PROVIDER_A", "MER000001", 1001, 800000, "VND");
        assertEquals("MOCKQR|v=1|merchant=MER000001|order=1001|amount=800000|currency=VND", qr);
    }

    @Test
    void qrProviderDoesNotAuthorizeCard() {
        var result = service.authorize("QR_PROVIDER_A", 1001, 800000, PaymentMethod.CARD, false);
        assertFalse(result.approved());
        assertEquals("PAYMENT_METHOD_NOT_SUPPORTED", result.failureCode());
    }
}
