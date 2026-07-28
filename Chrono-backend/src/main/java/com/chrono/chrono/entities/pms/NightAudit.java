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
        name = "pms_night_audits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_night_audit_property_date",
                columnNames = {"property_id", "business_date"}
        )
)
public class NightAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "arrivals_count", nullable = false)
    private long arrivalsCount;

    @Column(name = "departures_count", nullable = false)
    private long departuresCount;

    @Column(name = "in_house_count", nullable = false)
    private long inHouseCount;

    @Column(name = "no_show_count", nullable = false)
    private long noShowCount;

    @Column(name = "open_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openBalance;

    @Column(name = "closed_by", nullable = false, length = 120)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;
}
