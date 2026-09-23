package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "pms_invoice_lines")
public class PmsInvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PmsInvoice invoice;

    /** Original operational line remains linked even after a document correction. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_item_id")
    private FolioItem sourceItem;

    /** Nullable unique allocation: only one current document can bill a source line. */
    @Column(name = "active_source_item_id", unique = true)
    private Long activeSourceItemId;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "vat_rate", precision = 7, scale = 4)
    private BigDecimal vatRate;
    @Column(name = "service_date")
    private java.time.LocalDate serviceDate;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4) private BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4) private BigDecimal vatAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4) private BigDecimal grossAmount;
}
