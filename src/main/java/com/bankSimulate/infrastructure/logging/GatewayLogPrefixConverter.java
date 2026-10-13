package com.bankSimulate.infrastructure.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Map;
import java.util.regex.Pattern;

public class GatewayLogPrefixConverter extends ClassicConverter {

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_.:-]");

    @Override
    public String convert(ILoggingEvent event) {

        Map<String, String> mdc = event.getMDCPropertyMap();

        String bank = value(mdc, GatewayLogContext.BANK, "GATEWAY");

        String merNo = value(mdc, GatewayLogContext.MER_NO,"");
        String terNo = value(mdc, GatewayLogContext.TER_NO,"");
        String tradeNo = value(mdc, GatewayLogContext.TRADE_NO,"");
        String orderNo = value(mdc, GatewayLogContext.ORDER_NO,"");

        if (isBlank(merNo)
                && isBlank(terNo)
                && isBlank(tradeNo)
                && isBlank(orderNo)) {
            return "[" + bank + "]";
        }

        return "[%s][%s-%s-%s-%s]".formatted(
                bank,
                merNo,
                terNo,
                tradeNo,
                orderNo
        );
    }

    private boolean isBlank(String str) {
        return str.isBlank();
    }

    private String value(
            Map<String, String> mdc,
            String key, String defaultValue
    ) {
        String value = mdc.get(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return UNSAFE.matcher(value).replaceAll("_");
    }
}