package com.bankSimulate.infrastructure.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayLogPrefixConverterTest {
    private final GatewayLogPrefixConverter converter = new GatewayLogPrefixConverter();

    @Test
    void printsStructuredGatewayPrefix() {
        LoggingEvent event = new LoggingEvent();
        event.setMDCPropertyMap(Map.of(
                GatewayLogContext.SOURCE, "GATEWAY",
                GatewayLogContext.PROVIDER, "BANK-SIMULATE",
                GatewayLogContext.AREA, "PAYMENT",
                GatewayLogContext.RESOURCE_ID, "bs-1",
                GatewayLogContext.ORDER_CODE, "42",
                "requestId", "req-1"));

        assertThat(converter.convert(event))
                .isEqualTo("[GATEWAY][BANK-SIMULATE][area=PAYMENT][id=bs-1][orderCode=42][trace=req-1] ");
    }
}
