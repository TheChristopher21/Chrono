package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter @Table(name="pms_revenue_automation")
public class PmsRevenueAutomation {
 @Id @Column(name="property_id") private Long propertyId;
 @Version private long version;
 @Column(name="snapshots_enabled",nullable=false) private boolean snapshotsEnabled=true;
 @Column(name="pricing_enabled",nullable=false) private boolean pricingEnabled;
 @Column(name="horizon_days",nullable=false) private int horizonDays=30;
 @Column(name="min_samples",nullable=false) private int minSamples=8;
 @Column(name="rules_json",nullable=false,columnDefinition="TEXT") private String rulesJson="[]";
}
