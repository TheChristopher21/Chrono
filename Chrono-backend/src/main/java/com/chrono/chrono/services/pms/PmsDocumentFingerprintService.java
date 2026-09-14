package com.chrono.chrono.services.pms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class PmsDocumentFingerprintService {
    private final byte[] key;

    public PmsDocumentFingerprintService(
            @Value("${app.pms.document-hmac-key:}") String configuredKey,
            @Value("${app.production:false}") boolean production) {
        if (configuredKey == null || configuredKey.isBlank()) {
            if (production) {
                throw new IllegalStateException("APP_PMS_DOCUMENT_HMAC_KEY is required in production");
            }
            configuredKey = "local-development-document-hmac-key-only";
        }
        this.key = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public String fingerprint(String documentNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(documentNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available", exception);
        }
    }
}
