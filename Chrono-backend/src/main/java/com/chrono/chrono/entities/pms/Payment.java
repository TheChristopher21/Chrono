package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_payments", indexes = @Index(name = "idx_pms_payment_folio", columnList = "folio_id"))
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private Folio folio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.POSTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentKind kind = PaymentKind.PAYMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id")
    private Payment originalPayment;

    @Column(length = 120)
    private String reference;

    @Column(length = 500)
    private String reason;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "voided_by", length = 120)
    private String voidedBy;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
