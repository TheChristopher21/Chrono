package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name = "pms_housekeeping_task_events", indexes = @Index(name = "idx_pms_hk_events_task", columnList = "task_id,id"))
public class HousekeepingTaskEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false) private HousekeepingTask task;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 24) private HousekeepingWorkStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 24) private HousekeepingWorkStatus toStatus;
    @Column(name = "assigned_to", length = 120) private String assignedTo;
    @Column(length = 1000) private String notes;
    @Column(nullable = false, length = 120) private String actor;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
