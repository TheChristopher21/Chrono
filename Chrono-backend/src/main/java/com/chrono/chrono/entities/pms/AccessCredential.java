package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_access_credentials", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_access_provider_ref", columnNames = {"property_id", "provider_code", "external_reference"}))
public class AccessCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;
    @Column(name = "external_reference", nullable = false, length = 160)
    private String externalReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AccessCredentialStatus status = AccessCredentialStatus.ACTIVE;
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;
    @Column(name = "issued_by", nullable = false, length = 120)
    private String issuedBy;
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
    @Column(name = "revoked_by", length = 120)
    private String revokedBy;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist void prePersist() { issuedAt = LocalDateTime.now(); }
}
