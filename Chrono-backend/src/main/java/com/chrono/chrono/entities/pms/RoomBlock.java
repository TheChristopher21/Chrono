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
        name = "pms_room_blocks",
        indexes = {
                @Index(name = "idx_pms_room_block_room_dates", columnList = "room_id,start_date,end_date"),
                @Index(name = "idx_pms_room_block_property_status", columnList = "property_id,status")
        }
)
public class RoomBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RoomBlockType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomBlockStatus status = RoomBlockStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_by", length = 120)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
