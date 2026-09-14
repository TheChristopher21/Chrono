package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsAuditEvent;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class PmsAuditWriter {
    private final PmsAuditEventRepository repository;
    private final HotelPropertyRepository propertyRepository;
    private final byte[] hmacKey;

    public PmsAuditWriter(PmsAuditEventRepository repository,
                          HotelPropertyRepository propertyRepository,
                          @Value("${app.pms.audit-hmac-key:}") String configuredKey,
                          @Value("${app.production:false}") boolean production) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        if (configuredKey == null || configuredKey.isBlank()) {
            if (production) throw new IllegalStateException("APP_PMS_AUDIT_HMAC_KEY is required in production");
            configuredKey = "local-development-audit-hmac-key-only";
        }
        this.hmacKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PmsAuditEvent append(HotelProperty property,
                                String eventType,
                                String aggregateType,
                                String aggregateId,
                                String details) {
        propertyRepository.findByIdAndCompany_IdForUpdate(property.getId(), property.getCompany().getId())
                .orElseThrow(() -> new IllegalStateException("Audit property no longer exists"));
        // Keep the persisted value and the signed value identical on databases
        // whose timestamp columns do not retain nanoseconds.
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        String actor = currentActor();
        String safeDetails = details == null ? "{}" : details.substring(0, Math.min(details.length(), 4000));
        Long maximum = repository.findMaximumSequence(property.getId());
        long sequence = maximum == null ? 1L : maximum + 1L;
        String previousHash = repository
                .findFirstByProperty_IdAndSequenceNumberIsNotNullOrderBySequenceNumberDesc(property.getId())
                .map(PmsAuditEvent::getIntegrityHash).orElse(null);
        String canonical = canonical(property.getCompany().getId(), property.getId(), actor, eventType,
                aggregateType, aggregateId, createdAt, safeDetails, sequence, previousHash, 2);
        PmsAuditEvent event = new PmsAuditEvent(
                property.getCompany(), property, actor, eventType, aggregateType, aggregateId,
                safeDetails, hmac(canonical), sequence, previousHash, 2, createdAt);
        return repository.save(event);
    }

    public boolean hasValidIntegrityHash(PmsAuditEvent event) {
        if (event.getSignatureVersion() < 2 || event.getSequenceNumber() == null) {
            String legacy = event.getCompany().getId() + "|" + event.getProperty().getId() + "|"
                    + event.getActor() + "|" + event.getEventType() + "|" + event.getAggregateType() + "|"
                    + event.getAggregateId() + "|" + event.getCreatedAt() + "|" + event.getDetails();
            return sha256(legacy).equals(event.getIntegrityHash());
        }
        String canonical = canonical(event.getCompany().getId(), event.getProperty().getId(), event.getActor(),
                event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getCreatedAt(),
                event.getDetails(), event.getSequenceNumber(), event.getPreviousHash(), event.getSignatureVersion());
        return MessageDigest.isEqual(hmac(canonical).getBytes(StandardCharsets.US_ASCII),
                event.getIntegrityHash().getBytes(StandardCharsets.US_ASCII));
    }

    public boolean hasValidChain(List<PmsAuditEvent> events) {
        List<PmsAuditEvent> signed = events.stream()
                .filter(event -> event.getSignatureVersion() >= 2 && event.getSequenceNumber() != null)
                .sorted(Comparator.comparing(PmsAuditEvent::getSequenceNumber))
                .toList();
        for (int index = 1; index < signed.size(); index++) {
            PmsAuditEvent previous = signed.get(index - 1);
            PmsAuditEvent current = signed.get(index);
            if (current.getSequenceNumber() != previous.getSequenceNumber() + 1
                    || !previous.getIntegrityHash().equals(current.getPreviousHash())) {
                return false;
            }
        }
        return true;
    }

    private String canonical(Long companyId, Long propertyId, String actor, String eventType,
                             String aggregateType, String aggregateId, LocalDateTime createdAt, String details,
                             long sequence, String previousHash, int version) {
        return version + "|" + sequence + "|" + (previousHash == null ? "ROOT" : previousHash) + "|"
                + companyId + "|" + propertyId + "|" + actor + "|" + eventType + "|" + aggregateType + "|"
                + aggregateId + "|" + createdAt + "|" + details;
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 ist nicht verfügbar.", exception);
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return "SYSTEM";
        }
        return authentication.getName().trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }
}
