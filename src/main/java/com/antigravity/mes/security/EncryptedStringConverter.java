package com.antigravity.mes.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently encrypts a String field with AES-256-GCM on write and decrypts it on
 * read. Apply with {@code @Convert(converter = EncryptedStringConverter.class)} on any
 * entity field holding sensitive data (e.g. mobile number). Pair with a separate
 * HMAC-hash column (see {@link CryptoUtil#hmac(String)}) if the field also needs to be
 * looked up by equality, since the ciphertext itself is not searchable.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return CryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return CryptoUtil.decrypt(dbData);
    }
}
