package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_event_offers",uniqueConstraints=@UniqueConstraint(name="uk_pms_event_offer_version",columnNames={"resource_booking_id","offer_version"}))
public class PmsEventOffer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="resource_booking_id",nullable=false,updatable=false) private ResourceBooking booking;
 @Column(name="offer_version",nullable=false,updatable=false) private int offerVersion;
 @Column(name="resource_id",nullable=false,updatable=false) private Long resourceId;
 @Column(name="snapshot_json",nullable=false,updatable=false,columnDefinition="TEXT") private String snapshotJson;
 @Column(name="content_hash",nullable=false,updatable=false,length=64) private String contentHash;
 @Column(nullable=false,updatable=false,columnDefinition="TEXT") private String terms;
 @Column(name="valid_until",nullable=false,updatable=false) private LocalDateTime validUntil;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="created_by",nullable=false,updatable=false,length=120) private String createdBy;
 @Column(nullable=false,length=24) private String status="OPEN";
 @Column(name="token_hash",unique=true,length=64) private String tokenHash;
 @Column(name="decided_at") private LocalDateTime decidedAt;
 @Column(name="signature_name",length=180) private String signatureName;
 @Column(name="decision_note",length=1000) private String decisionNote;
 @Column(name="acceptance_hash",length=64) private String acceptanceHash;
 @Column(name="applied_at") private LocalDateTime appliedAt;
}
