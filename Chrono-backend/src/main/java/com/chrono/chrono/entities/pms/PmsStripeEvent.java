package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_stripe_events")
public class PmsStripeEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id",nullable=false,unique=true,length=180) private String eventId;
    @Column(name="account_id",length=180) private String accountId;
    @Column(name="event_type",nullable=false,length=100) private String eventType;
    @Column(name="object_id",nullable=false,length=180) private String objectId;
    @Column(name="request_key",length=80) private String requestKey;
    @Column(nullable=false,length=24) private String status="PENDING";
    @Column(nullable=false) private int attempts;
    @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt;
    @Column(name="received_at",nullable=false) private LocalDateTime receivedAt;
    @Column(name="last_error",length=500) private String lastError;
}
