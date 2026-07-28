package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_reservation_status_history",
        indexes = @Index(
                name = "idx_pms_reservation_status_history",
                columnList = "reservation_id,changed_at"
        )
)
public class ReservationStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private ReservationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private ReservationStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 120)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 500)
    private String reason;

    @PrePersist
    void prePersist() {
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
    }
}
