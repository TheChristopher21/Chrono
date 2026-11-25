package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="pms_delivery_attachments")
public class PmsDeliveryAttachment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="job_id") private PmsDeliveryJob job;
    @Column(nullable=false,length=180) private String filename;
    @Column(nullable=false,length=100) private String contentType;
    @Lob @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="longblob") private byte[] content;
    @Column(nullable=false,length=64) private String sha256;
}
