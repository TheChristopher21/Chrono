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
        name = "pms_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_invoice_property_number",
                columnNames = {"property_id", "invoice_number"}
        )
)
public class PmsInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private Folio folio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceType type = InvoiceType.INVOICE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private PmsInvoice originalInvoice;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "recipient_name", nullable = false, length = 180)
    private String recipientName;

    @Column(name = "recipient_address", length = 180)
    private String recipientAddress;

    @Column(name = "recipient_postal_code", length = 20)
    private String recipientPostalCode;

    @Column(name = "recipient_city", length = 120)
    private String recipientCity;

    @Column(name = "recipient_country_code", nullable = false, length = 2)
    private String recipientCountryCode = "CH";

    @Column(name = "recipient_snapshot", columnDefinition = "text")
    private String recipientSnapshot;
    @Column(name = "supplier_snapshot", columnDefinition = "text")
    private String supplierSnapshot;
    @Column(name = "supplier_tax_label", length = 40)
    private String supplierTaxLabel;
    @Column(name = "supplier_registration_number", length = 100)
    private String supplierRegistrationNumber;
    @Column(name = "service_from")
    private LocalDate serviceFrom;
    @Column(name = "service_to")
    private LocalDate serviceTo;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "language_code", nullable = false, length = 5)
    private String languageCode = "DE";

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4) private BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4) private BigDecimal vatAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4) private BigDecimal grossAmount;

    @Column(name = "vat_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "creditor_iban", length = 34)
    private String creditorIban;

    @Column(name = "qr_reference", length = 27)
    private String qrReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "correction_mode", length = 24)
    private String correctionMode;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    @Column(name = "corrected_by", length = 120)
    private String correctedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
