package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.ChannelConnection;
import com.chrono.chrono.entities.pms.ChannelConnectionStatus;
import com.chrono.chrono.repositories.pms.ChannelConnectionRepository;
import com.chrono.chrono.entities.pms.WebhookDelivery;
import com.chrono.chrono.repositories.pms.WebhookDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class PmsWebhookSecurityService {
    private final ChannelConnectionRepository connectionRepository;
    private final PmsSecretResolver secretResolver;
    private final Duration allowedClockSkew;
    private final WebhookDeliveryRepository deliveryRepository;

    public PmsWebhookSecurityService(
            ChannelConnectionRepository connectionRepository,
            PmsSecretResolver secretResolver,
            WebhookDeliveryRepository deliveryRepository,
            @Value("${app.pms.webhooks.allowed-clock-skew:PT5M}") Duration allowedClockSkew) {
        this.connectionRepository = connectionRepository;
        this.secretResolver = secretResolver;
        this.allowedClockSkew = allowedClockSkew;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public ChannelConnection verify(String webhookKey,
                                    String timestampHeader,
                                    String deliveryIdHeader,
                                    String signatureHeader,
                                    String rawBody) {
        String resolvedWebhookKey = webhookKey == null ? "" : webhookKey.trim();
        if (!resolvedWebhookKey.matches("^(?:[a-f0-9]{32}|legacy-[0-9]+)$")) {
            throw unauthorized("Unbekannte Schnittstellen-Verbindung.");
        }
        ChannelConnection connection = connectionRepository
                .findByWebhookKey(resolvedWebhookKey)
                .orElseThrow(() -> unauthorized("Unbekannte Schnittstellen-Verbindung."));
        if (connection.getStatus() != ChannelConnectionStatus.READY) {
            throw unauthorized("Die Schnittstellen-Verbindung ist nicht empfangsbereit.");
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
        String deliveryId = deliveryIdHeader == null ? "" : deliveryIdHeader.trim();
        if (!deliveryId.matches("^[A-Za-z0-9._:-]{16,100}$")) {
            throw unauthorized("Ungültige Webhook-Delivery-ID.");
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
        byte[] expected = hmac(secret, timestampHeader + "." + deliveryId + "." + rawBody);
        if (!MessageDigest.isEqual(expected, suppliedBytes)) {
            throw unauthorized("Webhook-Signatur stimmt nicht überein.");
        }
        if (deliveryRepository.existsByConnection_IdAndDeliveryId(connection.getId(), deliveryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook wurde bereits verarbeitet.");
        }
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setConnection(connection);
        delivery.setDeliveryId(deliveryId);
        deliveryRepository.saveAndFlush(delivery);
        return connection;
    }

    @Scheduled(cron = "${app.pms.webhooks.replay-cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void deleteExpiredReplayGuards() {
        deliveryRepository.deleteOlderThan(LocalDateTime.now().minusDays(14));
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
