package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
@Table(name="pms_delivery_jobs",uniqueConstraints=@UniqueConstraint(name="uk_pms_delivery_key",columnNames={"property_id","request_key"}),
 indexes=@Index(name="idx_pms_delivery_due",columnList="status,next_attempt_at"))
public class PmsDeliveryJob {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="invoice_id") private PmsInvoice invoice;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="communication_id") private GuestCommunication communication;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="receivable_id") private PmsReceivable receivable;
    @Column(precision=19,scale=4) private java.math.BigDecimal outstandingAtQueue;
    @Column(name="request_key",nullable=false,length=160) private String requestKey;
    @Column(nullable=false,length=32) private String kind;
    @Column(nullable=false,length=190) private String recipient;
    @Column(nullable=false,length=190) private String senderEmail;
    @Column(length=120) private String senderName;
    @Column(length=190) private String replyTo;
    @Column(nullable=false,length=240) private String subject;
    @Column(nullable=false,columnDefinition="text") private String body;
    @Column(nullable=false,length=24) private String status="QUEUED";
    @Column(nullable=false,length=190) private String messageId;
    @Column(nullable=false,length=64) private String payloadHash;
    private int attempts;
    @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    @Column(length=80) private String leaseOwner;
    @Column(length=1000) private String lastError;
    private LocalDateTime createdAt;
    @Column(length=120) private String createdBy;
    private LocalDateTime sentAt;
    @OneToMany(mappedBy="job",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<PmsDeliveryAttachment> attachments=new ArrayList<>();
}
