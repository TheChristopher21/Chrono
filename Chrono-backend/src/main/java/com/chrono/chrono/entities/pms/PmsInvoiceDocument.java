package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_invoice_documents")
public class PmsInvoiceDocument {
 @Id private Long invoiceId;
 @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id") private PmsInvoice invoice;
 @Lob @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="longblob") private byte[] content;
 @Column(nullable=false,length=64) private String sha256;
 private LocalDateTime createdAt;
}
