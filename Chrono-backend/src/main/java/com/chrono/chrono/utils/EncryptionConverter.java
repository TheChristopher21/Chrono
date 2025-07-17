package com.chrono.chrono.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;

@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {
    private static final String ALGO = "AES";
    private static final SecretKey KEY;

    static {
        String env = System.getenv("ENCRYPT_KEY");
        byte[] keyBytes = Arrays.copyOf((env != null ? env : "defaultsecretkey").getBytes(StandardCharsets.UTF_8), 16);
        KEY = new SecretKeySpec(keyBytes, ALGO);
    }

    private Cipher cipher(int mode) throws Exception {
        Cipher c = Cipher.getInstance(ALGO);
        c.init(mode, KEY);
        return c;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] enc = cipher(Cipher.ENCRYPT_MODE).doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] dec = cipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(dbData));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
