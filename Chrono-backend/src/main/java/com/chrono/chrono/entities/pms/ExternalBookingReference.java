package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_external_booking_references",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_external_booking_channel_id",
                columnNames = {"property_id", "channel_code", "external_id"}
        ),
        indexes = @Index(name = "idx_pms_external_booking_reservation", columnList = "reservation_id")
)
public class ExternalBookingReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "channel_code", nullable = false, length = 50)
    private String channelCode;

    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void prePersist() {
        receivedAt = LocalDateTime.now();
    }
}
