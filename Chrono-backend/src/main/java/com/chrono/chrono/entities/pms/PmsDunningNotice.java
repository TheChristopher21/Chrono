package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name="pms_dunning_notices",uniqueConstraints=@UniqueConstraint(name="uk_pms_dunning_level",columnNames={"receivable_id","level_number"}))
public class PmsDunningNotice {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="receivable_id") private PmsReceivable receivable;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id") private PmsInvoice invoice;
 @Column(name="level_number",nullable=false) private int level;
 private LocalDate businessDate;
 private LocalDate dueDate;
 @Column(precision=19,scale=4,nullable=false) private BigDecimal outstandingAmount;
 @Column(length=3,nullable=false) private String currencyCode;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delivery_id") private PmsDeliveryJob delivery;
 private LocalDateTime createdAt;
 @Column(length=120) private String createdBy;
}
