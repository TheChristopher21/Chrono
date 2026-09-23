package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_booking_engine_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_booking_engine_property", columnNames = "property_id"))
public class BookingEngineSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @Column(name = "public_slug", nullable = false, length = 120, unique = true)
    private String publicSlug;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "require_guarantee", nullable = false)
    private boolean requireGuarantee;
    @Column(name = "terms_url", length = 500)
    private String termsUrl;
    @Column(name = "privacy_url", length = 500)
    private String privacyUrl;
    @Column(name = "confirmation_message", length = 1000)
    private String confirmationMessage;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
