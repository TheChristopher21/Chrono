package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsAuditEvent;
import com.chrono.chrono.repositories.pms.PmsAuditEventRepository;
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

@Service
public class PmsAuditWriter {
    private final PmsAuditEventRepository repository;

    public PmsAuditWriter(PmsAuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PmsAuditEvent append(HotelProperty property,
                                String eventType,
                                String aggregateType,
                                String aggregateId,
                                String details) {
        // Keep the persisted value and the hashed value identical on databases
        // whose timestamp columns do not retain nanoseconds.
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        String actor = currentActor();
        String safeDetails = details == null ? "{}" : details.substring(0, Math.min(details.length(), 4000));
        String canonical = property.getCompany().getId() + "|" + property.getId() + "|" + actor + "|"
                + eventType + "|" + aggregateType + "|" + aggregateId + "|" + createdAt + "|" + safeDetails;
        PmsAuditEvent event = new PmsAuditEvent(
                property.getCompany(), property, actor, eventType, aggregateType, aggregateId,
                safeDetails, sha256(canonical), createdAt);
        return repository.save(event);
    }

    public boolean hasValidIntegrityHash(PmsAuditEvent event) {
        String canonical = event.getCompany().getId() + "|" + event.getProperty().getId() + "|"
                + event.getActor() + "|" + event.getEventType() + "|" + event.getAggregateType() + "|"
                + event.getAggregateId() + "|" + event.getCreatedAt() + "|" + event.getDetails();
        return sha256(canonical).equals(event.getIntegrityHash());
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
