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
        name = "pms_housekeeping_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_housekeeping_room_date",
                columnNames = {"room_id", "service_date"}
        ),
        indexes = @Index(name = "idx_pms_housekeeping_property_date", columnList = "property_id,service_date")
)
public class HousekeepingTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HousekeepingTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HousekeepingStatus status = HousekeepingStatus.DIRTY;

    @Column(nullable = false)
    private int priority = 50;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes = 30;

    @Column(length = 1000)
    private String notes;

    @Column(name = "assigned_to", length = 120)
    private String assignedTo;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
