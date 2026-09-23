package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One durable notification episode per hotel, alarm and destination channel. */
@Entity
@Getter
@Setter
@Table(name = "pms_operational_alert_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uk_pms_alert_delivery", columnNames = {"property_id", "alert_code", "channel"}))
public class PmsOperationalAlertDelivery {
    public enum Channel { EMAIL, SLACK, TEAMS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @Column(name = "alert_code", nullable = false, length = 100)
    private String alertCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;

    // Store a digest, never webhook credentials or email addresses.
    @Column(name = "destination_hash", nullable = false, length = 64)
    private String destinationHash;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int severity;

    @Column(name = "notified_severity", nullable = false)
    private int notifiedSeverity;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "claim_until")
    private LocalDateTime claimUntil;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;
}
