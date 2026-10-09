package com.bankSimulate.application;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.domain.enums.CredentialStatus;
import com.bankSimulate.domain.enums.Status;
import com.bankSimulate.domain.merchant.Merchant;
import com.bankSimulate.domain.merchant.MerchantCredential;
import com.bankSimulate.domain.terminal.Terminal;
import com.bankSimulate.infrastructure.persistence.MerchantCredentialRepository;
import com.bankSimulate.infrastructure.persistence.TerminalRepository;
import com.bankSimulate.infrastructure.security.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class GatewayRuntimeAuth {
    private final MerchantService merchants;
    private final MerchantCredentialRepository credentials;
    private final TerminalRepository terminals;
    private final SecretCipher cipher;
    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeAuth.class);

    public GatewayRuntimeAuth(MerchantService merchants, MerchantCredentialRepository credentials, TerminalRepository terminals, SecretCipher cipher) {
        this.merchants = merchants;
        this.credentials = credentials;
        this.terminals = terminals;
        this.cipher = cipher;
    }

    public Access authenticate(String merNo, String terminalId, String suppliedSecret) {
        Merchant merchant = merchants.require(merNo);
        log.info("Xác thực merchant name='{}' status={}", merchant.getName(), merchant.getStatus());

        if (merchant.getStatus() != Status.ACTIVE) {
            throw new ApiException(409, "MERCHANT_INACTIVE", "Merchant is inactive");
        }

        Terminal terminal = terminals.findByTerminalId(terminalId).orElseThrow(() -> new ApiException(404, "TERMINAL_NOT_FOUND", "Terminal " + terminalId + " not found"));

        if (!terminal.getMerchantId().equals(merchant.getId())) {
            throw new ApiException(403, "TERMINAL_NOT_OWNED", "Terminal does not belong to merchant");
        }

        if (terminal.getStatus() != Status.ACTIVE) {
            throw new ApiException(409, "TERMINAL_INACTIVE", "Terminal is inactive");
        }

        MerchantCredential credential = credentials.findFirstByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE).orElseThrow(() -> new ApiException(401, "INVALID_MERCHANT_CREDENTIAL", "Merchant credential is invalid"));

        String expectedSecret = cipher.decrypt(credential.getSecretCiphertext());

        if (suppliedSecret == null || !MessageDigest.isEqual(expectedSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "INVALID_MERCHANT_CREDENTIAL", "Merchant credential is invalid");
        }

        return new Access(merchant, terminal, expectedSecret);
    }

    public String activeSecret(Merchant merchant) {
        return activeSecret(merchant.getId());
    }

    public String activeSecret(java.util.UUID merchantId) {
        MerchantCredential credential = credentials.findFirstByMerchantIdAndStatus(merchantId, CredentialStatus.ACTIVE).orElseThrow(() -> new ApiException(500, "MERCHANT_CREDENTIAL_MISSING", "Merchant credential is missing"));
        return cipher.decrypt(credential.getSecretCiphertext());
    }

    public record Access(Merchant merchant, Terminal terminal, String secret) {
    }
}
