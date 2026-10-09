package com.bankSimulate.application;

import com.bankSimulate.domain.enums.*;
import com.bankSimulate.domain.acquirer.*;
import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.persistence.*;
import com.bankSimulate.infrastructure.web.dto.AdminDtos;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcquirerService {
    private static final Logger log = LoggerFactory.getLogger(AcquirerService.class);
    private final AcquirerRepository acquirers;
    private final AcquirerMerchantConfigRepository configs;
    private final MerchantService merchants;

    public AcquirerService(AcquirerRepository acquirers, AcquirerMerchantConfigRepository configs, MerchantService merchants) {
        this.acquirers = acquirers;
        this.configs = configs;
        this.merchants = merchants;
    }

    @Transactional
    public AdminDtos.AcquirerResponse create(AdminDtos.CreateAcquirerRequest request) {
        String code = request.code().trim();
        if (!code.matches("[A-Za-z0-9_-]{2,64}"))
            throw new ApiException(400, "INVALID_ACQUIRER_CODE", "Acquirer code must be 2-64 letters, digits, '-' or '_'");
        if (acquirers.findByCode(code).isPresent())
            throw new ApiException(409, "ACQUIRER_CODE_EXISTS", "Acquirer code already exists");
        var methods = ConfigParsers.methods(request.paymentMethods());
        Acquirer a = new Acquirer(code, request.name().trim(), methods, request.threeDsSupported());
        applyBankBin(a, request.bankBin());
        acquirers.save(a);
        log.info("Acquirer created code={} methods={} threeDsSupported={} bankBin={}", a.getCode(), methods,
                a.isThreeDsSupported(), a.getBankBin());
        return response(a);
    }

    @Transactional
    public AdminDtos.AcquirerResponse update(String code, AdminDtos.UpdateAcquirerRequest request) {
        Acquirer a = require(code);
        var methods = request.paymentMethods() == null ? null : ConfigParsers.methods(request.paymentMethods());
        Status status = request.status() == null ? null : ConfigParsers.enumValue(Status.class, request.status(), "INVALID_STATUS");
        String name = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        a.update(name, methods, request.threeDsSupported(), status);
        if (request.bankBin() != null) applyBankBin(a, request.bankBin());
        log.info("Acquirer updated code={} methods={} threeDsSupported={} status={} bankBin={}",
                a.getCode(), a.getPaymentMethods(), a.isThreeDsSupported(), a.getStatus(), a.getBankBin());
        return response(a);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AcquirerResponse> list() {
        return acquirers.findAll().stream()
                .sorted(java.util.Comparator.comparing(Acquirer::getCode))
                .map(this::response).toList();
    }

    @Transactional
    public AdminDtos.AcquirerConfigResponse configure(String merNo, AdminDtos.AcquirerConfigRequest request) {
        var merchant = merchants.require(merNo);
        Acquirer a = require(request.acquirerCode());
        if (a.getStatus() != Status.ACTIVE) throw new ApiException(409, "ACQUIRER_INACTIVE", "Acquirer is inactive");
        if (configs.existsByAcquirerIdAndMerchantId(a.getId(), merchant.getId()))
            throw new ApiException(409, "ACQUIRER_CONFIG_EXISTS", "Merchant is already configured for this acquirer");
        configs.save(new AcquirerMerchantConfig(a.getId(), merchant.getId(), request.mid(), request.tid()));
        return new AdminDtos.AcquirerConfigResponse(a.getCode(), a.getName(), methodNames(a), request.mid(), request.tid(), configStatus());
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AcquirerConfigResponse> listConfigs(String merNo) {
        var merchant = merchants.require(merNo);
        return configs.findAllByMerchantId(merchant.getId()).stream()
                .map(c -> acquirers.findById(c.getAcquirerId())
                        .map(a -> new AdminDtos.AcquirerConfigResponse(a.getCode(), a.getName(), methodNames(a),
                                c.getMid(), c.getTid(), c.getStatus().name()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(AdminDtos.AcquirerConfigResponse::acquirerCode))
                .toList();
    }

    public Acquirer require(String code) {
        return acquirers.findByCode(code).orElseThrow(() -> new ApiException(404, "ACQUIRER_NOT_FOUND", "Acquirer not found"));
    }

    public java.util.Optional<Acquirer> requireById(java.util.UUID id) {
        return acquirers.findById(id);
    }

    private void applyBankBin(Acquirer a, String raw) {
        String bin = raw == null ? "" : raw.trim();
        if (!bin.isEmpty() && !bin.matches("[0-9]{6}"))
            throw new ApiException(400, "INVALID_BANK_BIN", "Bank BIN must be 6 digits");
        if (!bin.isEmpty() && acquirers.findAll().stream()
                .anyMatch(other -> !other.getId().equals(a.getId()) && bin.equals(other.getBankBin())))
            throw new ApiException(409, "BANK_BIN_IN_USE", "Another acquirer already uses BIN " + bin);
        a.changeBankBin(bin);
    }

    private AdminDtos.AcquirerResponse response(Acquirer a) {
        return new AdminDtos.AcquirerResponse(a.getCode(), a.getName(), a.getStatus().name(), methodNames(a),
                a.isThreeDsSupported(), a.getBankBin());
    }

    private static List<String> methodNames(Acquirer a) {
        return a.getPaymentMethods().stream().sorted().map(Enum::name).toList();
    }

    private String configStatus() {
        return Status.ACTIVE.name();
    }
}
