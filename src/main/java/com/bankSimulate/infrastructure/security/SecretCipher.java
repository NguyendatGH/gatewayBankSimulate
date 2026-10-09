package com.bankSimulate.infrastructure.security;

import com.bankSimulate.domain.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Component
public class SecretCipher {
    private final String masterKey;
    public SecretCipher(@Value("${gateway.master-key:}") String masterKey) { this.masterKey = masterKey; }
    public String encrypt(String plaintext) {
        try {
            if (masterKey.isBlank()) throw new ApiException(500, "MASTER_KEY_NOT_CONFIGURED", "Gateway master key is not configured");
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Unable to encrypt merchant secret", e); }
    }
    public String decrypt(String ciphertext) {
        try {
            if (masterKey.isBlank()) throw new ApiException(500, "MASTER_KEY_NOT_CONFIGURED", "Gateway master key is not configured");
            String[] parts = ciphertext.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            if (e instanceof ApiException api) throw api;
            throw new ApiException(500, "CREDENTIAL_DECRYPT_FAILED", "Unable to read merchant credential");
        }
    }
    private SecretKeySpec key() throws NoSuchAlgorithmException { return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES"); }
}
