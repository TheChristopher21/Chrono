package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_audit_events", indexes = {
        @Index(name = "idx_pms_audit_property_created", columnList = "property_id,created_at"),
        @Index(name = "idx_pms_audit_company_created", columnList = "company_id,created_at")
})
public class PmsAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false, updatable = false)
    private HotelProperty property;

    @Column(nullable = false, length = 120, updatable = false)
    private String actor;

    @Column(name = "event_type", nullable = false, length = 80, updatable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80, updatable = false)
    private String aggregateId;

    @Column(nullable = false, length = 4000, updatable = false)
    private String details;

    @Column(name = "integrity_hash", nullable = false, length = 64, updatable = false)
    private String integrityHash;

    @Column(name = "sequence_number", updatable = false)
    private Long sequenceNumber;

    @Column(name = "previous_hash", length = 64, columnDefinition = "char(64)", updatable = false)
    private String previousHash;

    @Column(name = "signature_version", nullable = false, updatable = false)
    private int signatureVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PmsAuditEvent() {
    }

    public PmsAuditEvent(Company company,
                         HotelProperty property,
                         String actor,
                         String eventType,
                         String aggregateType,
                         String aggregateId,
                         String details,
                         String integrityHash,
                         Long sequenceNumber,
                         String previousHash,
                         int signatureVersion,
                         LocalDateTime createdAt) {
        this.company = company;
        this.property = property;
        this.actor = actor;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.details = details;
        this.integrityHash = integrityHash;
        this.sequenceNumber = sequenceNumber;
        this.previousHash = previousHash;
        this.signatureVersion = signatureVersion;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public HotelProperty getProperty() { return property; }
    public String getActor() { return actor; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getDetails() { return details; }
    public String getIntegrityHash() { return integrityHash; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public String getPreviousHash() { return previousHash; }
    public int getSignatureVersion() { return signatureVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
