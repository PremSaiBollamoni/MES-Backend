package com.antigravity.mes.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field encryption for genuinely sensitive columns (e.g. employee address,
 * emergency contact number). Encryption is non-deterministic (fresh random IV per call)
 * so two ciphertexts of the same plaintext never look alike - this is what makes
 * {@link EncryptedStringConverter} safe to use on columns. Values encrypted here are not
 * meant to be looked up by equality (unlike the old mobile-number login, now replaced by
 * a plain-text system-generated username - see V5 migration).
 */
final class CryptoUtil {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static SecretKey aesKey;
    private static SecureRandom secureRandom;

    private CryptoUtil() {
    }

    static void init(SecretKey aesKey, SecureRandom secureRandom) {
        CryptoUtil.aesKey = aesKey;
        CryptoUtil.secureRandom = secureRandom;
    }

    static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        requireInitialized();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt field", e);
        }
    }

    static String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        requireInitialized();
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt field - data may be corrupt or key mismatched", e);
        }
    }

    private static void requireInitialized() {
        if (aesKey == null || secureRandom == null) {
            throw new IllegalStateException("CryptoUtil not initialized - CryptoConfig must run first");
        }
    }
}
