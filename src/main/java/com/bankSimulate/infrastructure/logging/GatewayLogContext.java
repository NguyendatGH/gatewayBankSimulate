package com.bankSimulate.infrastructure.logging;

import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GatewayLogContext {
    public static final String BANK = "bank";
    public static final String MER_NO = "merNo";
    public static final String TER_NO = "terNo";
    public static final String TRADE_NO = "tradeNo";
    public static final String ORDER_NO = "orderNo";

    public static final String GATEWAY = "GATEWAY";
    public static final String MERCHANT = "MERCHANT";

    private static final String[] KEYS = {BANK, MER_NO, TER_NO, TRADE_NO, ORDER_NO};

    private GatewayLogContext() {
    }

    public static Scope open(
            String bank,
            String merNo,
            String terNo,
            String tradeNo,
            String orderNo
    ) {
        Map<String, String> previous = copyCurrentContext();

        putOrRemove(BANK, bankTag(bank));
        putOrRemove(MER_NO, merNo);
        putOrRemove(TER_NO, terNo);
        putOrRemove(TRADE_NO, tradeNo);
        putOrRemove(ORDER_NO, orderNo);

        return new Scope(previous);
    }

    public static Scope open(String merNo, String terNo, String orderNo
    ) {
        return open(
                GATEWAY,
                merNo,
                terNo,
                null,
                orderNo
        );
    }

    public static void setTradeNo(String tradeNo) {
        putOrRemove(TRADE_NO, tradeNo);
    }

    public static void setBank(String bank) {
        putOrRemove(BANK, bankTag(bank));
    }

    public static Scope withBank(String bank) {
        Map<String, String> previous = copyCurrentContext();

        putOrRemove(BANK, bankTag(bank));

        return new Scope(previous);
    }

    public static void setMerchantNo(String merNo) {
        putOrRemove(MER_NO, merNo);
    }

    public static void setTerminalNo(String terNo) {
        putOrRemove(TER_NO, terNo);
    }

    public static void setOrderNo(String orderNo) {
        putOrRemove(ORDER_NO, orderNo);
    }

    public static void clearPaymentContext() {
        MDC.remove(BANK);
        MDC.remove(MER_NO);
        MDC.remove(TER_NO);
        MDC.remove(TRADE_NO);
        MDC.remove(ORDER_NO);
    }

    private static String bankTag(String bank) {
        return bank == null ? null : bank.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static Map<String, String> copyCurrentContext() {
        Map<String, String> current = MDC.getCopyOfContextMap();

        if (current == null) {
            return Collections.emptyMap();
        }

        return new HashMap<>(current);
    }

    private static void restore(Map<String, String> previous) {
        MDC.clear();

        if (previous != null && !previous.isEmpty()) {
            MDC.setContextMap(previous);
        }
    }

    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previous;
        private boolean closed;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            restore(previous);
            closed = true;
        }
    }

}
