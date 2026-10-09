package com.bankSimulate.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record CreateMerchantRequest(@NotBlank String name, String webhookUrl) {
    }

    public record UpdateMerchantRequest(String name, String webhookUrl, String status) {
    }

    public record MerchantResponse(String merNo, String name, String status, String webhookUrl,
                                   String externalReference, SettlementAccountResponse settlementAccount,
                                   String defaultTerminalId, List<String> channelTerminalIds) {
    }

    public record SettlementAccountRequest(@NotBlank String bankBin, @NotBlank String accountNumber, String accountName) {
    }

    public record SettlementAccountResponse(String bankBin, String accountNumberMasked, String accountName) {
    }

    public record MerchantListItem(String merNo, String name, String status, String externalReference,
                                   long terminalCount) {
    }

    public record MerchantCreatedResponse(String merNo, String name, String status, String webhookUrl,
                                          String merchantSecret) {
    }

    public record CredentialResponse(String merNo, int credentialVersion, String merchantSecret) {
    }

    public record CreateTerminalRequest(@NotBlank String name, @NotBlank String channel, @NotBlank String currency,
                                        @NotEmpty List<String> paymentMethods, String threeDsPolicy,
                                        String routingProfileCode, String acquirerCode,
                                        @Valid SettlementAccountRequest settlementAccount) {
        public CreateTerminalRequest(String name, String channel, String currency, List<String> paymentMethods,
                                     String threeDsPolicy, String routingProfileCode) {
            this(name, channel, currency, paymentMethods, threeDsPolicy, routingProfileCode, null, null);
        }
    }

    public record UpdateTerminalRequest(String name, String channel, String currency, String status) {
    }

    public record DeleteTerminalRequest(String name, String status){

    }

    public record PaymentMethodsRequest(@NotEmpty List<String> paymentMethods) {
    }

    public record ThreeDsRequest(String threeDsPolicy) {
    }

    public record RoutingProfileRequest(String routingProfileCode) {
    }

    public record TerminalConfigurationRequest(@NotEmpty List<String> paymentMethods, String threeDsPolicy,
                                               String routingProfileCode, String acquirerCode,
                                               @Valid SettlementAccountRequest settlementAccount) {
    }

    public record TerminalResponse(String terminalId, String merNo, String name, String channel, String currency,
                                   List<String> paymentMethods, String threeDsPolicy, String routingProfileCode,
                                   String status, RoutingProfileSummary routingProfile, List<String> routableMethods,
                                   String acquirerCode, SettlementAccountResponse settlementAccount,
                                   String purpose, boolean retired) {
    }

    public record DefaultTerminalRequest(@NotBlank String terminalId) {
    }

    public record RoutingProfileSummary(String code, String name, String status,
                                        java.util.Map<String, List<RouteResponse>> routes) {
    }

    public record RoutingProfileListItem(String code, String name, String status) {
    }

    public record OnboardingRequest(@NotBlank String name, String externalReference, String channel, String currency,
                                    String webhookUrl, SettlementAccountRequest settlementAccount) {
    }

    public record OnboardingResponse(String merchantNo, String terminalId, String merchantSecret,
                                     String routingProfileCode, List<String> paymentMethods, String threeDsPolicy) {
    }

    public record CreateAcquirerRequest(@NotBlank String code, @NotBlank String name,
                                        @NotEmpty List<String> paymentMethods, boolean threeDsSupported, String bankBin) {
    }

    public record UpdateAcquirerRequest(String name, List<String> paymentMethods, Boolean threeDsSupported,
                                        String status, String bankBin) {
    }

    public record AcquirerResponse(String code, String name, String status, List<String> paymentMethods,
                                   boolean threeDsSupported, String bankBin) {
    }

    public record AcquirerConfigRequest(@NotBlank String acquirerCode, String mid, String tid) {
    }

    public record AcquirerConfigResponse(String acquirerCode, String acquirerName, List<String> paymentMethods,
                                         String mid, String tid, String status) {
    }

    public record CreateRoutingProfileRequest(@NotBlank String code, @NotBlank String name) {
    }

    public record RoutingRuleRequest(@NotBlank String paymentMethod, @NotBlank String acquirerCode,
                                     @Min(1) int priority) {
    }

    public record RouteResponse(int priority, String acquirerCode) {
    }

    public record RoutingProfileResponse(String code, String status,
                                         java.util.Map<String, List<RouteResponse>> routes) {
    }
}
