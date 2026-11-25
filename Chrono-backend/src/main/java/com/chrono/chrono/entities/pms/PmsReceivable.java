package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(name="pms_receivables", indexes=@Index(name="idx_pms_receivable_property_org_due",columnList="property_id,organization_id,due_date"))
public class PmsReceivable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="organization_id",nullable=false) private PmsOrganization organization;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="invoice_id",nullable=false,unique=true) private PmsInvoice invoice;
    @Column(nullable=false,precision = 19, scale = 4) private BigDecimal amount;
    @Column(name="settled_amount",nullable=false,precision = 19, scale = 4) private BigDecimal settledAmount=BigDecimal.ZERO;
    @Column(name="credited_amount",nullable=false,precision = 19, scale = 4) private BigDecimal creditedAmount=BigDecimal.ZERO;
    @Column(name="due_date",nullable=false) private LocalDate dueDate;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="created_by",nullable=false,length=120) private String createdBy;
    @Column(name="reminder_level",nullable=false) private int reminderLevel;
    @Column(name="last_reminder_at") private LocalDateTime lastReminderAt;
    public BigDecimal balance() { return amount.subtract(settledAmount).subtract(creditedAmount); }
}
