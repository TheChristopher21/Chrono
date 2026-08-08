package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(
        name = "pms_rate_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_rate_override_plan_date",
                columnNames = {"rate_plan_id", "stay_date"}
        ),
        indexes = @Index(name = "idx_pms_rate_override_date", columnList = "rate_plan_id,stay_date")
)
public class RateOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_plan_id", nullable = false)
    private RatePlan ratePlan;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "min_stay", nullable = false)
    private int minStay = 1;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "closed_arrival", nullable = false)
    private boolean closedArrival;

    @Column(name = "closed_departure", nullable = false)
    private boolean closedDeparture;
}
