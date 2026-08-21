package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_group_bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_group_property_code",
                columnNames = {"property_id", "group_code"}
        ),
        indexes = @Index(name = "idx_pms_group_property_dates", columnList = "property_id,arrival_date,departure_date")
)
public class GroupBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_guest_id", nullable = false)
    private GuestProfile contactGuest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private PmsOrganization organization;

    @Column(name = "group_code", nullable = false, length = 40)
    private String groupCode;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GroupBookingStatus status = GroupBookingStatus.CONFIRMED;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
