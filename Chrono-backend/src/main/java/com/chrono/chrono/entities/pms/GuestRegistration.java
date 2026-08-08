package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_guest_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_guest_registration_reservation",
                columnNames = "reservation_id"
        )
)
public class GuestRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GuestRegistrationStatus status = GuestRegistrationStatus.PENDING;

    @Column(name = "address_line", length = 180)
    private String addressLine;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String city;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "nationality_code", length = 2)
    private String nationalityCode;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(name = "document_last_four", length = 4)
    private String documentLastFour;

    @Column(name = "vehicle_plate", length = 32)
    private String vehiclePlate;

    @Column(name = "signature_name", length = 180)
    private String signatureName;

    @Column(name = "privacy_consent_at")
    private LocalDateTime privacyConsentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by", length = 120)
    private String completedBy;

    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "invited_by", length = 120)
    private String invitedBy;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "rule_code", length = 40)
    private String ruleCode;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion = 1;
}
