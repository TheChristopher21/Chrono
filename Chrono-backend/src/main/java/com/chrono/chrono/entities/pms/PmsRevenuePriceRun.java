package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
@Entity @Getter @Setter @Table(name="pms_revenue_price_runs",uniqueConstraints=@UniqueConstraint(name="uk_pms_price_run",columnNames={"property_id","business_date"}))
public class PmsRevenuePriceRun {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="property_id",nullable=false) private Long propertyId;
 @Column(name="business_date",nullable=false) private LocalDate businessDate;
 @Column(nullable=false) private int applied;
}
