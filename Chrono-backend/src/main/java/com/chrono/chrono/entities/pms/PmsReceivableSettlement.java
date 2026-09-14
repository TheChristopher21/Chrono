package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name="pms_receivable_settlements", uniqueConstraints=@UniqueConstraint(name="uk_pms_ar_settlement_request",columnNames={"property_id","request_id"}), indexes=@Index(name="idx_pms_ar_settlement_property_date",columnList="property_id,posting_date"))
public class PmsReceivableSettlement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="receivable_id",nullable=false) private PmsReceivable receivable;
    @Column(nullable=false,precision = 19, scale = 4) private BigDecimal amount;
    @Column(name="posting_date",nullable=false) private LocalDate postingDate;
    @Column(name="request_id",nullable=false,length=64) private String requestId;
    @Column(name="bank_reference",nullable=false,length=180) private String bankReference;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="created_by",nullable=false,length=120) private String createdBy;
}
