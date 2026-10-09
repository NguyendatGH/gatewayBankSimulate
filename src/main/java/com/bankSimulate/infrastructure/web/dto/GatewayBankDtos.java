package com.bankSimulate.infrastructure.web.dto;

import java.util.List;

public final class GatewayBankDtos {

    private GatewayBankDtos() {
    }

    public record SupportedBank(String bin, String name) {
    }

    public record SupportedBanksResponse(List<String> bins, List<SupportedBank> banks) {
    }

    public record TerminalInfoResponse(String merNo, String terminalId, String channel, String currency,
                                       List<String> paymentMethods, String threeDsPolicy,
                                       List<String> configuredPaymentMethods) {
    }
}
