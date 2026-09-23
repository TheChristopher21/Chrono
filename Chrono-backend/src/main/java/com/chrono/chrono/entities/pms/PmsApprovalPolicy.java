package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Entity @Getter @Setter @Table(name="pms_approval_policies")
public class PmsApprovalPolicy {
    @Id @Column(name="property_id") private Long propertyId;
    @Version private Long version;
    @Column(nullable=false) private boolean enabled;
    @Column(name="refund_threshold",nullable=false,precision=19,scale=4) private BigDecimal refundThreshold=BigDecimal.valueOf(500);
}
