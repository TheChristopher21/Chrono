package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_resource_bookings", indexes = @Index(
        name = "idx_pms_resource_booking_dates", columnList = "resource_id,start_at,end_at"))
public class ResourceBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private HotelResource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_booking_id")
    private GroupBooking groupBooking;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "organizer_name", nullable = false, length = 180)
    private String organizerName;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int attendees = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceBookingStatus status = ResourceBookingStatus.CONFIRMED;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
