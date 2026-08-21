package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(
        name = "pms_rate_plans",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_rate_plan_property_code",
                columnNames = {"property_id", "code"}
        ),
        indexes = {
                @Index(name = "idx_pms_rate_plan_property", columnList = "property_id"),
                @Index(name = "idx_pms_rate_plan_room_type", columnList = "room_type_id")
        }
)
public class RatePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "nightly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyRate;

    @Column(name = "min_stay", nullable = false)
    private int minStay = 1;

    @Column(name = "breakfast_included", nullable = false)
    private boolean breakfastIncluded;

    @Column(nullable = false)
    private boolean refundable = true;

    @Column(nullable = false)
    private boolean active = true;
}
