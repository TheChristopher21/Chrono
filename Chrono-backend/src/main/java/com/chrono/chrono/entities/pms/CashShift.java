package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_cash_shifts",
        indexes = @Index(name = "idx_pms_cash_shift_property_status", columnList = "property_id,status")
)
public class CashShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @Column(name = "register_code", nullable = false, length = 32)
    private String registerCode = "FRONTDESK";

    @Column(name = "outlet_code", nullable = false, length = 32)
    private String outletCode = "FRONTDESK";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CashShiftStatus status = CashShiftStatus.OPEN;

    @Column(name = "opened_by", nullable = false, length = 120)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "opening_float", nullable = false, precision = 19, scale = 4) private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "closed_by", length = 120)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "expected_cash", precision = 19, scale = 4) private BigDecimal expectedCash;

    @Column(name = "actual_cash", precision = 19, scale = 4) private BigDecimal actualCash;

    @Column(precision = 19, scale = 4) private BigDecimal variance;

    @Column(length = 500)
    private String notes;

    @PrePersist
    void prePersist() {
        if (openedAt == null) {
            openedAt = LocalDateTime.now();
        }
    }
}
