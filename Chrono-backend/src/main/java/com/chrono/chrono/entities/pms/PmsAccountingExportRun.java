package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name="pms_accounting_export_runs",uniqueConstraints=@UniqueConstraint(name="uk_pms_export_request",columnNames={"property_id","request_key"}))
public class PmsAccountingExportRun {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
 @Column(name="request_key",nullable=false,length=120) private String requestKey;
 private LocalDate fromDate;
 private LocalDate toExclusive;
 @Column(nullable=false,length=24) private String status="GENERATED";
 @Lob @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="longblob") private byte[] content;
 @Column(nullable=false,length=64) private String sha256;
 private LocalDateTime createdAt;
 @Column(length=120) private String createdBy;
 private LocalDateTime acknowledgedAt;
 @Column(length=120) private String acknowledgedBy;
 @Column(length=190) private String acknowledgementReference;
}
