package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name="pms_bank_transactions",uniqueConstraints=@UniqueConstraint(name="uk_pms_bank_transaction",columnNames={"property_id","bank_account","external_id"}))
public class PmsBankTransaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="import_id") private PmsBankImport bankImport;
 @Column(name="bank_account",nullable=false,length=100) private String bankAccount;
 @Column(name="external_id",nullable=false,length=160) private String externalId;
 private LocalDate bookingDate;
 @Column(nullable=false,length=3) private String currencyCode;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
 @Column(length=500) private String reference;
 @Column(length=180) private String debtorName;
 @Column(nullable=false,length=24) private String status="OPEN";
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="receivable_id") private PmsReceivable receivable;
 private LocalDateTime matchedAt;
 @Column(length=120) private String matchedBy;
}
