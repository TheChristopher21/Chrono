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
@Table(name = "pms_folio_items", indexes = @Index(name = "idx_pms_folio_item_folio", columnList = "folio_id"))
public class FolioItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private Folio folio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reservation_id")
    private Reservation sourceReservation;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FolioItemType type;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal totalAmount;

    @Column(name = "tax_rate", precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_included", nullable = false)
    private boolean taxIncluded = true;

    @Column(name = "rate_generated", nullable = false)
    private boolean rateGenerated;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (sourceReservation == null && folio != null) sourceReservation = folio.getReservation();
        createdAt = LocalDateTime.now();
    }
}
