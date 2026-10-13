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

    @Column(name = "nightly_rate", nullable = false, precision = 19, scale = 4) private BigDecimal nightlyRate;

    @Column(name = "min_stay", nullable = false)
    private int minStay = 1;

    @Column(name = "breakfast_included", nullable = false)
    private boolean breakfastIncluded;

    @Column(nullable = false)
    private boolean refundable = true;

    @Column(nullable = false)
    private boolean active = true;

    // Null means not configured; never infer a tax jurisdiction from the guest.
    @Column(name = "vat_rate", precision = 7, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "tax_included", nullable = false)
    private boolean taxIncluded = true;

    /** Allocated breakfast portion of nightlyRate, in the same gross/net basis. */
    @Column(name = "breakfast_amount", nullable = false, precision = 19, scale = 4) private BigDecimal breakfastAmount = BigDecimal.ZERO;

    @Column(name = "breakfast_vat_rate", precision = 7, scale = 4)
    private BigDecimal breakfastVatRate;

    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDate bookingFrom;
    private LocalDate bookingTo;
    private Integer maxStay;
    private Integer minAdvanceDays;
    private Integer maxAdvanceDays;

    @Column(name = "included_adults", nullable = false)
    private int includedAdults = 1;

    @Column(name = "extra_adult_rate", nullable = false, precision = 19, scale = 4) private BigDecimal extraAdultRate = BigDecimal.ZERO;

    @Column(name = "child_rate", nullable = false, precision = 19, scale = 4) private BigDecimal childRate = BigDecimal.ZERO;

    private Integer cancellationDeadlineHours;
    @Column(precision = 7, scale = 4)
    private BigDecimal cancellationFeePercent;
    @Column(precision = 7, scale = 4)
    private BigDecimal depositPercent;
    private Integer paymentDueDays;
    @Column(precision = 7, scale = 4)
    private BigDecimal noShowFeePercent;
    @Column(precision = 7, scale = 4)
    private BigDecimal policyFeeTaxRate;
    private Integer depositDueDaysBeforeArrival;
    @Column(length = 2000)
    private String cancellationPolicy;
    @Column(length = 2000)
    private String paymentPolicy;
    @Column(length = 4000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private PmsOrganization organization;
}
