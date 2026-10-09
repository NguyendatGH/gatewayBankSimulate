package com.bankSimulate.application;

import com.bankSimulate.domain.enums.*;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.merchant.*;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.security.SecretCipher;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class MerchantService {
    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);
    private final MerchantRepository merchants;
    private final TerminalRepository terminals;
    private final MerchantCredentialRepository credentials;
    private final SecretCipher cipher;
    private final GatewayMoneyService money;
    private final SecureRandom random = new SecureRandom();

    public MerchantService(MerchantRepository merchants, MerchantCredentialRepository credentials, SecretCipher cipher,
                           TerminalRepository terminals, GatewayMoneyService money) {
        this.merchants = merchants;
        this.credentials = credentials;
        this.cipher = cipher;
        this.terminals = terminals;
        this.money = money;
    }

    @Transactional
    public AdminDtos.MerchantCreatedResponse create(AdminDtos.CreateMerchantRequest request) {
        return create(request, null);
    }

    public AdminDtos.MerchantCreatedResponse create(AdminDtos.CreateMerchantRequest request, String externalReference) {
        requireName(request.name());
        String webhook = ConfigParsers.webhook(request.webhookUrl());
        Merchant merchant = merchants.save(new Merchant("MerNo" + String.format("%06d", merchants.nextNumber()), request.name().trim(), webhook, externalReference));
        String secret = newSecret();
        credentials.save(new MerchantCredential(merchant.getId(), 1, cipher.encrypt(secret)));
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merchant.getMerNo(), null, null)) {
            log.info("Merchant created");
        }
        return new AdminDtos.MerchantCreatedResponse(merchant.getMerNo(), merchant.getName(), merchant.getStatus().name(), merchant.getWebhookUrl(), secret);
    }

    @Transactional(readOnly = true)
    public Merchant require(String merNo) {
        return merchants.findByMerNo(merNo).orElseThrow(() -> new ApiException(404, "MERCHANT_NOT_FOUND", "Merchant " + merNo + " not found"));
    }

    @Transactional(readOnly = true)
    public Merchant requireById(java.util.UUID id) {
        return merchants.findById(id).orElseThrow(() -> new ApiException(404, "MERCHANT_NOT_FOUND", "Merchant not found"));
    }

    public AdminDtos.MerchantResponse get(String merNo) {
        return response(require(merNo));
    }

    @Transactional
    public AdminDtos.MerchantResponse update(String merNo, AdminDtos.UpdateMerchantRequest request) {
        Merchant merchant = require(merNo);
        if (request.name() != null) requireName(request.name());
        merchant.update(request.name() == null ? null : request.name().trim(), request.webhookUrl() == null ? null : ConfigParsers.webhook(request.webhookUrl()),
                request.status() == null ? null : ConfigParsers.enumValue(Status.class, request.status(), "INVALID_STATUS"));
        return response(merchant);
    }

    @Transactional
    public AdminDtos.MerchantResponse updateSettlement(String merNo, AdminDtos.SettlementAccountRequest request) {
        Merchant merchant = require(merNo);
        applySettlement(merchant, request);

        int released = money.releaseHeldFunds(merchant.getId(), merchant.getSettlementBankBin(),
                merchant.getSettlementAccountNumber());
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merNo, null, null)) {
            log.info("Settlement account changed bankBin={} account={} releasedHeldPayments={}", merchant.getSettlementBankBin(),
                    ConfigParsers.maskAccount(merchant.getSettlementAccountNumber()), released);
        }
        return response(merchant);
    }

    private void applySettlement(Merchant merchant, AdminDtos.SettlementAccountRequest request) {
        if (request == null) return;
        ConfigParsers.Settlement settlement = ConfigParsers.settlement(request);
        merchant.changeSettlementAccount(settlement.bankBin(), settlement.accountNumber(), settlement.accountName());
    }

    @Transactional
    public AdminDtos.CredentialResponse rotate(String merNo) {
        Merchant merchant = require(merNo);
        credentials.findFirstByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE).ifPresent(MerchantCredential::revoke);
        credentials.flush();
        int version = credentials.findFirstByMerchantIdOrderByCredentialVersionDesc(merchant.getId()).map(c -> c.getCredentialVersion() + 1).orElse(1);
        String secret = newSecret();
        credentials.save(new MerchantCredential(merchant.getId(), version, cipher.encrypt(secret)));
        try (GatewayLogContext.Scope ignored = GatewayLogContext.open(merNo, null, null)) {
            log.info("Merchant credential rotated: version={}", version);
        }
        return new AdminDtos.CredentialResponse(merNo, version, secret);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.MerchantListItem> list() {
        return merchants.findAll().stream()
                .sorted(java.util.Comparator.comparing(Merchant::getMerNo))
                .map(m -> new AdminDtos.MerchantListItem(m.getMerNo(), m.getName(), m.getStatus().name(),
                        m.getExternalReference(), terminals.countByMerchantId(m.getId())))
                .toList();
    }

    private AdminDtos.MerchantResponse response(Merchant m) {
        AdminDtos.SettlementAccountResponse settlement = m.hasSettlementAccount()
                ? new AdminDtos.SettlementAccountResponse(m.getSettlementBankBin(), ConfigParsers.maskAccount(m.getSettlementAccountNumber()),
                m.getSettlementAccountName())
                : null;
        return new AdminDtos.MerchantResponse(m.getMerNo(), m.getName(), m.getStatus().name(), m.getWebhookUrl(),
                m.getExternalReference(), settlement);
    }


    private String newSecret() {
        byte[] value = new byte[24];
        random.nextBytes(value);
        return "gwsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void requireName(String name) {
        if (name == null || name.isBlank())
            throw new ApiException(400, "INVALID_MERCHANT_NAME", "Merchant name is required");
    }
}
