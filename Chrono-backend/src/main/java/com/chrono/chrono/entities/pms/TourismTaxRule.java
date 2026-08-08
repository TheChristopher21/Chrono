package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_tourism_tax_rules", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_tourism_tax_property", columnNames = "property_id"))
public class TourismTaxRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 120)
    private String name = "Kurtaxe";
    @Column(name = "adult_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal adultRate = BigDecimal.ZERO;
    @Column(name = "child_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal childRate = BigDecimal.ZERO;
    @Column(name = "child_free_under", nullable = false)
    private int childFreeUnder = 16;
    @Column(name = "maximum_nights")
    private Integer maximumNights;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
