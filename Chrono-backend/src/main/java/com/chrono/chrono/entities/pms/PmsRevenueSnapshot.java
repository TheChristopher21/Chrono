package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity @Getter @Setter
@Table(name="pms_revenue_snapshots",uniqueConstraints=@UniqueConstraint(name="uk_pms_revenue_snapshot_day",columnNames={"property_id","as_of_date"}))
public class PmsRevenueSnapshot {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
 @Column(name="as_of_date",nullable=false) private LocalDate asOfDate;
 @Column(name="to_exclusive",nullable=false) private LocalDate toExclusive;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(name="captured_at",nullable=false) private LocalDateTime capturedAt;
 @Column(name="captured_by",nullable=false,length=120) private String capturedBy;
 @OneToMany(mappedBy="snapshot",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("stayDate ASC") private List<PmsRevenueSnapshotDay> days=new ArrayList<>();
}
