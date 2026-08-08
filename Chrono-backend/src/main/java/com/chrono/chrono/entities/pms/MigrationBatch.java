package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_migration_batches", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_migration_batch_key", columnNames = {"property_id", "idempotency_key"}))
public class MigrationBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MigrationBatchStatus status;
    @Column(name = "imported_guests", nullable = false)
    private int importedGuests;
    @Column(name = "imported_reservations", nullable = false)
    private int importedReservations;
    @Column(name = "imported_payments", nullable = false)
    private int importedPayments;
    @Column(name = "total_opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalOpeningBalance = BigDecimal.ZERO;
    @Column(name = "reconciliation_message", length = 1000)
    private String reconciliationMessage;
    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
}
