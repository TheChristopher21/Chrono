package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_reservation_confirmation",
                columnNames = {"property_id", "confirmation_code"}
        ),
        indexes = {
                @Index(name = "idx_pms_reservation_property_dates", columnList = "property_id,arrival_date,departure_date"),
                @Index(name = "idx_pms_reservation_room_dates", columnList = "room_id,arrival_date,departure_date"),
                @Index(name = "idx_pms_reservation_guest", columnList = "guest_id")
        }
)
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private GuestProfile guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDate ASC")
    private java.util.List<ReservationRoomSegment> roomSegments = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ReservationGuest> coGuests = new java.util.ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_plan_id", nullable = false)
    private RatePlan ratePlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_booking_id")
    private GroupBooking groupBooking;

    @Column(name = "confirmation_code", nullable = false, length = 40)
    private String confirmationCode;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private int adults = 1;

    @Column(nullable = false)
    private int children;

    @Column(name = "child_ages", length = 100)
    private String childAges;

    @Column(name = "guest_preference_snapshot", length = 1000)
    private String guestPreferenceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationSource source = ReservationSource.DIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "guarantee_status", nullable = false, length = 32)
    private ReservationGuaranteeStatus guaranteeStatus = ReservationGuaranteeStatus.UNGUARANTEED;

    @Column(name = "hold_until")
    private LocalDateTime holdUntil;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "no_show_at")
    private LocalDateTime noShowAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    private LocalDateTime policySnapshotAt;
    private Integer policyCancellationDeadlineHours;
    @Column(precision = 7, scale = 4) private BigDecimal policyCancellationFeePercent;
    @Column(precision = 7, scale = 4) private BigDecimal policyNoShowFeePercent;
    @Column(precision = 7, scale = 4) private BigDecimal policyFeeTaxRate;
    @Column(precision = 7, scale = 4) private BigDecimal policyDepositPercent;
    private Integer policyDepositDueDaysBeforeArrival;
    private java.time.LocalTime policyCheckInTime;
}
