package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_tourism_tax_postings", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_tourism_tax_reservation", columnNames = "reservation_id"))
public class TourismTaxPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "folio_id", nullable = false)
    private Folio folio;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "folio_item_id", nullable = false)
    private FolioItem folioItem;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private int nights;
    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;
    @Column(name = "posted_by", nullable = false, length = 120)
    private String postedBy;

    @PrePersist void prePersist() { if (postedAt == null) postedAt = LocalDateTime.now(); }
}
