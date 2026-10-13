package com.bankSimulate.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

public final class ChannelDtos {
    private ChannelDtos() {
    }

    public record OpenChannelRequest(String bankCode, List<String> paymentMethods, String accountName, String accountNumber) {
    }

    public record UpdateChannelRequest(List<String> paymentMethods, String accountName, String accountNumber) {
    }

    public record ChannelResponse(String id, String bankCode, String bankName, String bankBin,
                                  List<String> paymentMethods, List<String> routableMethods,
                                  String accountName, String accountNumberMasked, boolean primary,
                                  Instant openedAt, String status) {
    }

    public record BankOption(String code, String name, String bankBin, List<String> paymentMethods, boolean threeDsSupported) {
    }

    public record ChannelsResponse(List<ChannelResponse> channels, List<BankOption> banks, List<String> customerMethods) {
    }

    public record PaymentMethodsResponse(List<String> paymentMethods) {
    }
}
