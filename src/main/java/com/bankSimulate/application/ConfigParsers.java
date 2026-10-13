package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.PaymentMethod;
import com.bankSimulate.domain.enums.ThreeDsPolicy;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;

import java.net.URI;
import java.util.*;

public final class ConfigParsers {
    private ConfigParsers() {
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String value, String code) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, code, "Unsupported value: " + value);
        }
    }

    public static String currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}"))
            throw new ApiException(400, "INVALID_CURRENCY", "Currency must be three uppercase letters");
        return value;
    }

    public static String webhook(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "INVALID_WEBHOOK_URL", "Webhook URL must be an HTTP(S) URL");
        }
        return value;
    }

    public static EnumSet<PaymentMethod> methods(List<String> values) {
        if (values == null || values.isEmpty())
            throw new ApiException(400, "PAYMENT_METHOD_NOT_SUPPORTED", "At least one payment method is required");
        EnumSet<PaymentMethod> result = EnumSet.noneOf(PaymentMethod.class);
        for (String value : values)
            if (!result.add(enumValue(PaymentMethod.class, value, "PAYMENT_METHOD_NOT_SUPPORTED")))
                throw new ApiException(400, "PAYMENT_METHOD_NOT_SUPPORTED", "Duplicate payment method");
        return result;
    }

    public static void cardPolicy(EnumSet<PaymentMethod> methods, ThreeDsPolicy policy) {
        if (!methods.contains(PaymentMethod.CARD) && policy != null && policy != ThreeDsPolicy.DISABLED)
            throw new ApiException(400, "CARD_NOT_ENABLED", "3DS policy requires CARD to be enabled");
    }

    public record Settlement(String bankBin, String accountNumber, String accountName) {
    }

    public static Settlement settlement(AdminDtos.SettlementAccountRequest request) {
        String bin = request.bankBin() == null ? "" : request.bankBin().trim();
        String account = request.accountNumber() == null ? "" : request.accountNumber().replaceAll("\\s+", "");
        if (!bin.matches("[0-9]{6}"))
            throw new ApiException(400, "INVALID_SETTLEMENT_ACCOUNT", "Settlement bank BIN must be 6 digits");
        if (!account.matches("[0-9]{6,30}"))
            throw new ApiException(400, "INVALID_SETTLEMENT_ACCOUNT", "Settlement account number must be 6-30 digits");
        String name = request.accountName() == null || request.accountName().isBlank() ? null : request.accountName().trim();
        return new Settlement(bin, account, name);
    }

    public static String maskAccount(String accountNumber) {
        if (accountNumber == null) return null;
        return "••••" + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}
