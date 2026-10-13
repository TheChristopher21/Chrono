package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_financial_approvals",uniqueConstraints=@UniqueConstraint(name="ux_pms_approval_request",columnNames={"property_id","request_key"}))
public class PmsFinancialApproval {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
    @Column(name="request_key",nullable=false,length=80) private String requestKey;
    @Column(name="payment_id",nullable=false) private Long paymentId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="cash_shift_id") private Long cashShiftId;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="requested_by",nullable=false,length=120) private String requestedBy;
    @Column(nullable=false,length=20) private String status="PENDING";
    @Column(name="decided_by",length=120) private String decidedBy;
    @Column(name="decision_reason",length=500) private String decisionReason;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="decided_at") private LocalDateTime decidedAt;
    @Column(name="consumed_at") private LocalDateTime consumedAt;
}
