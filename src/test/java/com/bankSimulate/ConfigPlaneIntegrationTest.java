package com.bankSimulate;

import com.bankSimulate.application.*;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.web.AdminDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"gateway.admin-key=test-admin", "gateway.master-key=test-master-key"})
class ConfigPlaneIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_lab").withUsername("payment_lab").withPassword("payment_lab");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MerchantService merchants;
    @Autowired AcquirerService acquirers;
    @Autowired RoutingService routing;
    @Autowired TerminalService terminals;
    @Autowired MerchantCredentialRepository credentials;

    @Test
    void createsAndReadsCompleteConfiguration() {
        var merchant = merchants.create(new AdminDtos.CreateMerchantRequest("Encore Ticketing", "http://localhost:8080/webhook"));
        assertTrue(merchant.merNo().startsWith("MER"));
        assertTrue(merchant.merchantSecret().startsWith("gwsec_"));
        assertEquals(merchant.merNo(), merchants.get(merchant.merNo()).merNo());

        var a = acquirers.create(new AdminDtos.CreateAcquirerRequest("ACQUIRER_A", "Mock A", "CARD_ACQUIRER"));
        var b = acquirers.create(new AdminDtos.CreateAcquirerRequest("ACQUIRER_B", "Mock B", "CARD_ACQUIRER"));
        acquirers.configure(merchant.merNo(), new AdminDtos.AcquirerConfigRequest(a.code(), "MID_A", "TID_A"));
        acquirers.configure(merchant.merNo(), new AdminDtos.AcquirerConfigRequest(b.code(), "MID_B", "TID_B"));

        routing.create(new AdminDtos.CreateRoutingProfileRequest("VN_WEB_DEFAULT", "Vietnam Web Default"));
        routing.addRule("VN_WEB_DEFAULT", new AdminDtos.RoutingRuleRequest("CARD", a.code(), 1));
        routing.addRule("VN_WEB_DEFAULT", new AdminDtos.RoutingRuleRequest("CARD", b.code(), 2));

        var terminal = terminals.create(merchant.merNo(), new AdminDtos.CreateTerminalRequest("Encore Web VND", "WEB", "VND", List.of("CARD"), "REQUIRED", "VN_WEB_DEFAULT"));
        assertEquals("CARD", terminal.paymentMethods().getFirst());
        assertEquals("REQUIRED", terminal.threeDsPolicy());
        assertEquals("VN_WEB_DEFAULT", terminal.routingProfileCode());
        assertEquals(2, routing.get("VN_WEB_DEFAULT").routes().get("CARD").size());
        assertEquals(1, credentials.findFirstByMerchantIdOrderByCredentialVersionDesc(merchants.require(merchant.merNo()).getId()).orElseThrow().getCredentialVersion());
    }
}
