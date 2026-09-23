package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
@Entity @Getter @Setter @Table(name="pms_rate_inheritance",uniqueConstraints=@UniqueConstraint(name="uk_pms_rate_inheritance_target",columnNames="target_rate_id"))
public class PmsRateInheritance {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private long version;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="source_rate_id",nullable=false) private RatePlan source;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="target_rate_id",nullable=false) private RatePlan target;
 @Column(nullable=false) private boolean enabled;
 @Column(nullable=false) private boolean frozen;
 @Column(name="inherit_policy",nullable=false) private boolean inheritPolicy;
 @Column(name="manual_fx_rate",nullable=false,precision=19,scale=8) private BigDecimal manualFxRate;
 @Column(name="fx_date",nullable=false) private LocalDate fxDate;
 @Column(name="fx_valid_until",nullable=false) private LocalDate fxValidUntil;
 @Column(name="fx_reference",nullable=false,length=240) private String fxReference;
 @Column(name="adjustment_percent",nullable=false,precision=9,scale=4) private BigDecimal adjustmentPercent;
 @Column(name="min_price",nullable=false,precision=19,scale=4) private BigDecimal minPrice;
 @Column(name="max_price",nullable=false,precision=19,scale=4) private BigDecimal maxPrice;
 @Column(name="last_source_hash",length=64) private String lastSourceHash;
 @Column(name="last_applied_at") private LocalDateTime lastAppliedAt;
 @Column(name="last_result",length=1000) private String lastResult;
}
