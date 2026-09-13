package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name = "pms_payment_requests", uniqueConstraints = @UniqueConstraint(name = "uk_pms_payment_request_key", columnNames = "request_key"))
public class PmsPaymentRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "folio_id", nullable = false) private Folio folio;
    @Column(name = "request_key", nullable = false, length = 80) private String requestKey;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 32) private String status = "REQUESTED";
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(name = "capture_amount", precision = 19, scale = 4) private BigDecimal captureAmount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "provider_session_id", length = 160, unique = true) private String providerSessionId;
    @Column(name = "provider_payment_intent_id", length = 160) private String providerPaymentIntentId;
    @Column(name = "checkout_url", length = 3000) private String checkoutUrl;
    @Column(name = "provider_status", length = 64) private String providerStatus;
    @Column(name = "merchant_context", length = 180) private String merchantContext;
    @Column(name = "automation_error", length = 500) private String automationError;
    @Column(name = "last_checked_at") private LocalDateTime lastCheckedAt;
    @Column(name = "action_requested", length = 20) private String actionRequested;
    @Column(name = "created_by", nullable = false, length = 120) private String createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}
