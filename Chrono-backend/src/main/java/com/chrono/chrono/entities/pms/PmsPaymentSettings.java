package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_payment_settings")
public class PmsPaymentSettings {
    @Id @Column(name="property_id") private Long propertyId;
    @Column(name="merchant_context",length=180) private String merchantContext;
    @Column(name="verified_at") private LocalDateTime verifiedAt;
    @Column(name="automatic_deposit_links",nullable=false) private boolean automaticDepositLinks;
    @Column(name="deposit_cursor",nullable=false) private long depositCursor;
    @Column(name="last_automation_at") private LocalDateTime lastAutomationAt;
    @Column(name="last_automation_error",length=500) private String lastAutomationError;
    @Version private long version;
}
