package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Getter @Setter
@Table(name="pms_group_allotments",indexes=@Index(name="idx_pms_allotment_group_dates",columnList="group_booking_id,start_date,end_date"))
public class PmsGroupAllotment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="group_booking_id",nullable=false) private GroupBooking groupBooking;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_type_id",nullable=false) private RoomType roomType;
    @Column(name="start_date",nullable=false) private LocalDate startDate;
    @Column(name="end_date",nullable=false) private LocalDate endDate;
    @Column(nullable=false) private int quantity;
    @Column(name="release_date",nullable=false) private LocalDate releaseDate;
    @Column(nullable=false) private boolean released;
}
