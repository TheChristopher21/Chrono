package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_structured_invoices")
public class PmsStructuredInvoice {
 @Id @Column(name="invoice_id") private Long invoiceId;
 @Column(name="xml_document",nullable=false,updatable=false,columnDefinition="TEXT") private String xmlDocument;
 @Column(name="content_hash",nullable=false,updatable=false,length=64) private String contentHash;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="created_by",nullable=false,updatable=false,length=120) private String createdBy;
 @Column(nullable=false,updatable=false,length=40) private String standard="OASIS-UBL-2.1";
}
