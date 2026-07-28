package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.entities.pms.ChannelConnectionStatus;
import com.chrono.chrono.repositories.pms.ChannelConnectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class PmsWebhookSecurityService {
    private final ChannelConnectionRepository connectionRepository;
    private final PmsSecretResolver secretResolver;
    private final Duration allowedClockSkew;

    public PmsWebhookSecurityService(
            ChannelConnectionRepository connectionRepository,
            PmsSecretResolver secretResolver,
            @Value("${app.pms.webhooks.allowed-clock-skew:PT5M}") Duration allowedClockSkew) {
        this.connectionRepository = connectionRepository;
        this.secretResolver = secretResolver;
        this.allowedClockSkew = allowedClockSkew;
    }

    public ChannelConnection verify(String propertyCode,
                                    String providerCode,
                                    String timestampHeader,
                                    String signatureHeader,
                                    String rawBody) {
        ChannelConnection connection = connectionRepository
                .findByProperty_CodeIgnoreCaseAndProviderCodeIgnoreCase(propertyCode, providerCode)
                .orElseThrow(() -> unauthorized("Unbekannte Channel-Verbindung."));
        if (connection.getStatus() != ChannelConnectionStatus.READY) {
            throw unauthorized("Channel-Verbindung ist nicht empfangsbereit.");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException | NullPointerException exception) {
            throw unauthorized("Ungültiger Webhook-Zeitstempel.");
        }
        Instant sentAt = Instant.ofEpochSecond(timestamp);
        Duration difference = Duration.between(sentAt, Instant.now()).abs();
        if (difference.compareTo(allowedClockSkew) > 0) {
            throw unauthorized("Webhook-Zeitfenster ist abgelaufen.");
        }
        String secret = secretResolver.resolve(connection.getSecretReference())
                .orElseThrow(() -> unauthorized("Webhook-Secret ist nicht verfügbar."));
        String supplied = signatureHeader == null ? "" : signatureHeader.trim();
        if (supplied.startsWith("sha256=")) {
            supplied = supplied.substring(7);
        }
        byte[] suppliedBytes;
        try {
            suppliedBytes = HexFormat.of().parseHex(supplied);
        } catch (IllegalArgumentException exception) {
            throw unauthorized("Ungültige Webhook-Signatur.");
        }
        byte[] expected = hmac(secret, timestampHeader + "." + rawBody);
        if (!MessageDigest.isEqual(expected, suppliedBytes)) {
            throw unauthorized("Webhook-Signatur stimmt nicht überein.");
        }
        return connection;
    }

    private byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 ist nicht verfügbar.", exception);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
