package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;import java.time.*;
@Entity @Getter @Setter @Table(name="pms_beds24_publications")
public class PmsBeds24Publication {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="property_id",nullable=false) private Long propertyId;
 @Column(name="request_key",nullable=false,length=80) private String requestKey;
 @Column(name="external_property_id",nullable=false) private Long externalPropertyId;
 @Column(name="secret_reference",nullable=false,length=180) private String secretReference;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(nullable=false,columnDefinition="LONGTEXT") private String payload;
 @Column(name="from_date",nullable=false) private LocalDate fromDate;
 @Column(name="to_date",nullable=false) private LocalDate toDate;
 @Column(nullable=false,length=24) private String status="PENDING";
 @Column(nullable=false) private int attempts;
 @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="completed_at") private LocalDateTime completedAt;
 @Column(name="last_error",length=500) private String lastError;
 @PrePersist void prepare(){createdAt=LocalDateTime.now();if(nextAttemptAt==null)nextAttemptAt=createdAt;}
}
