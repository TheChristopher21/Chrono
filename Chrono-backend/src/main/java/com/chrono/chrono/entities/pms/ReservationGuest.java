package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name = "pms_reservation_guests", uniqueConstraints = @UniqueConstraint(name = "uk_pms_reservation_guest", columnNames = {"reservation_id", "guest_id"}))
public class ReservationGuest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false) private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_id", nullable = false) private GuestProfile guest;
    @Column(name = "arrival_date", nullable = false) private LocalDate arrivalDate;
    @Column(name = "departure_date", nullable = false) private LocalDate departureDate;
    @Column(name = "is_child", nullable = false) private boolean child;
    @Column(name = "address_line", length = 180) private String addressLine;
    @Column(name = "postal_code", length = 20) private String postalCode;
    @Column(length = 120) private String city;
    @Column(name = "country_code", length = 2) private String countryCode;
    @Column(name = "nationality_code", length = 2) private String nationalityCode;
    @Column(name = "document_hash", length = 64) private String documentHash;
    @Column(name = "document_last_four", length = 4) private String documentLastFour;
    @Column(name = "signature_name", length = 180) private String signatureName;
    @Column(name = "registration_completed_at") private LocalDateTime registrationCompletedAt;
}
