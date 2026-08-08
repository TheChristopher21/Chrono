package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "pms_pos_tickets", uniqueConstraints =
        @UniqueConstraint(name = "uk_pms_pos_ticket_number", columnNames = {"property_id", "ticket_number"}))
public class PosTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "folio_id")
    private Folio folio;
    @Column(name = "ticket_number", nullable = false, length = 50)
    private String ticketNumber;
    @Column(name = "outlet_code", nullable = false, length = 32)
    private String outletCode;
    @Column(name = "table_reference", length = 60)
    private String tableReference;
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PosTicketStatus status = PosTicketStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", length = 24)
    private PaymentMethod paymentMethod;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;
    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PosTicketLine> lines = new ArrayList<>();

    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
}
