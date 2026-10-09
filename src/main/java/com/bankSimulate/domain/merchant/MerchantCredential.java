package com.bankSimulate.domain.merchant;


import com.bankSimulate.domain.enums.CredentialStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "merchant_credentials")
public class MerchantCredential {
    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
    private String secretCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MerchantCredential() {
    }

    public MerchantCredential(UUID merchantId, int version, String ciphertext) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.credentialVersion = version;
        this.secretCiphertext = ciphertext;
        this.status = CredentialStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        status = CredentialStatus.REVOKED;
        revokedAt = Instant.now();
    }


}
