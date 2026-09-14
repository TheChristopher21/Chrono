package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.HealthStatus;
import com.chrono.chrono.dto.pms.PmsOperationalHealthResponse.OperationalAlert;
import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery;
import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery.Channel;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PmsOperationalAlertDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class PmsOperationalAlertDeliveryService {
    private final HotelPropertyRepository propertyRepository;
    private final PmsOperationalAlertDeliveryRepository deliveryRepository;
    private final Duration leaseDuration;

    public PmsOperationalAlertDeliveryService(HotelPropertyRepository propertyRepository,
                                             PmsOperationalAlertDeliveryRepository deliveryRepository,
                                             @Value("${app.pms.alerts.delivery-lease:PT10M}") Duration leaseDuration) {
        if (leaseDuration.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("PMS alarm delivery lease must be at least one minute.");
        }
        this.propertyRepository = propertyRepository;
        this.deliveryRepository = deliveryRepository;
        this.leaseDuration = leaseDuration;
    }

    public record ClaimedAlert(Long id, String token, int severity, OperationalAlert alert) {}
    public record DeliveryBatch(Long companyId, Long propertyId, Channel channel,
                                String destination, List<ClaimedAlert> alerts) {}

    /** Reserve inside a short transaction; callers perform network I/O only after it commits. */
    @Transactional
    public List<DeliveryBatch> claim(Long companyId, Long propertyId,
                                     PmsOperationalHealthResponse health, Map<Channel, String> destinations) {
        var property = propertyRepository.findByIdAndCompany_IdForUpdate(propertyId, companyId).orElseThrow();
        Map<String, OperationalAlert> current = new HashMap<>();
        health.alerts().stream().filter(alert -> severity(alert.severity()) > 0)
                .forEach(alert -> current.put(alert.code(), alert));
        var rows = deliveryRepository.findAllByProperty_Id(propertyId);
        Map<String, PmsOperationalAlertDelivery> existing = new HashMap<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (var row : rows) {
            existing.put(key(row.getAlertCode(), row.getChannel()), row);
            // A concurrent check which started earlier must not resolve a newer episode.
            if (health.checkedAt().isBefore(row.getObservedAt())) continue;
            row.setObservedAt(health.checkedAt());
            if (!current.containsKey(row.getAlertCode())) {
                row.setActive(false);
                row.setSeverity(0);
                row.setNotifiedSeverity(0);
                row.setClaimToken(null);
                row.setClaimUntil(null);
            }
        }
        List<DeliveryBatch> batches = new ArrayList<>();
        for (Channel channel : Channel.values()) {
            String destination = destinations.get(channel);
            if (destination == null || destination.isBlank()) continue;
            destination = destination.trim();
            String hash = digest(destination);
            List<ClaimedAlert> claimed = new ArrayList<>();
            for (OperationalAlert alert : current.values().stream()
                    .sorted(Comparator.comparing(OperationalAlert::code)).toList()) {
                var row = existing.get(key(alert.code(), channel));
                if (row != null && health.checkedAt().isBefore(row.getObservedAt())) continue;
                if (row == null) {
                    row = new PmsOperationalAlertDelivery();
                    row.setProperty(property);
                    row.setAlertCode(alert.code());
                    row.setChannel(channel);
                }
                if (!row.isActive() || !hash.equals(row.getDestinationHash())) {
                    row.setNotifiedSeverity(0);
                    row.setClaimToken(null);
                    row.setClaimUntil(null);
                    row.setNotifiedAt(null);
                }
                row.setActive(true);
                row.setDestinationHash(hash);
                row.setObservedAt(health.checkedAt());
                row.setSeverity(severity(alert.severity()));
                if (row.getSeverity() > row.getNotifiedSeverity()
                        && (row.getClaimUntil() == null || !row.getClaimUntil().isAfter(now))) {
                    row.setClaimToken(UUID.randomUUID().toString());
                    row.setClaimUntil(now.plus(leaseDuration));
                    deliveryRepository.save(row);
                    claimed.add(new ClaimedAlert(row.getId(), row.getClaimToken(), row.getSeverity(), alert));
                }
            }
            if (!claimed.isEmpty()) batches.add(new DeliveryBatch(companyId, propertyId,
                    channel, destination, List.copyOf(claimed)));
        }
        return List.copyOf(batches);
    }

    @Transactional
    public void delivered(DeliveryBatch batch) {
        propertyRepository.findByIdAndCompany_IdForUpdate(batch.propertyId(), batch.companyId()).orElseThrow();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (var alert : batch.alerts()) {
            deliveryRepository.acknowledge(alert.id(), alert.token(), alert.severity(), now);
        }
    }

    @Transactional
    public void failed(DeliveryBatch batch) {
        propertyRepository.findByIdAndCompany_IdForUpdate(batch.propertyId(), batch.companyId()).orElseThrow();
        for (var alert : batch.alerts()) deliveryRepository.release(alert.id(), alert.token());
    }

    private static int severity(HealthStatus severity) {
        return severity == HealthStatus.CRITICAL ? 2 : severity == HealthStatus.WARNING ? 1 : 0;
    }

    private static String key(String code, Channel channel) { return code + ":" + channel; }

    private static String digest(String destination) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(destination.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
