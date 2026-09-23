package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Getter @Setter
@Table(name = "pms_reservation_room_segments", indexes = @Index(name = "idx_pms_segment_room_dates", columnList = "room_id,start_date,end_date"))
public class ReservationRoomSegment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false) private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false) private Room room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "rate_plan_id", nullable = false) private RatePlan ratePlan;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(length = 500) private String reason;
    @Column(name = "created_by", nullable = false, length = 120) private String createdBy;
}
