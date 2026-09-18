package com.antigravity.mes.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Loads the AES-256 data-encryption key from externalized configuration (environment
 * variables in production) and hands it to {@link CryptoUtil}, which performs the
 * actual encrypt/decrypt operations. Keeping key material out of the JPA converter
 * keeps the converter free of Spring dependency-injection plumbing (JPA does not
 * manage AttributeConverter beans).
 *
 * In production, replace the property-based key with a managed KMS
 * (AWS KMS / Azure Key Vault / HashiCorp Vault) and inject the unwrapped key at boot.
 */
@Component
public class CryptoConfig {

    private final String aesKeyBase64;

    public CryptoConfig(@Value("${app.encryption.key}") String aesKeyBase64) {
        this.aesKeyBase64 = aesKeyBase64;
    }

    @PostConstruct
    void init() {
        byte[] aesKeyBytes = Base64.getDecoder().decode(aesKeyBase64);
        if (aesKeyBytes.length != 32) {
            throw new IllegalStateException("app.encryption.key must decode to 32 bytes (AES-256)");
        }
        CryptoUtil.init(new SecretKeySpec(aesKeyBytes, "AES"), new SecureRandom());
    }
}
