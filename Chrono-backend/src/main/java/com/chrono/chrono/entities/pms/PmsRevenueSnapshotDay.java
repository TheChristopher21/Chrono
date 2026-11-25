package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Getter @Setter
@Table(name="pms_revenue_snapshot_days",uniqueConstraints=@UniqueConstraint(name="uk_pms_revenue_snapshot_stay_day",columnNames={"snapshot_id","stay_date"}))
public class PmsRevenueSnapshotDay {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="snapshot_id",nullable=false) private PmsRevenueSnapshot snapshot;
 @Column(name="stay_date",nullable=false) private LocalDate stayDate;
 @Column(name="room_nights",nullable=false) private long roomNights;
 @Column(name="net_room_revenue",precision=19,scale=4) private BigDecimal netRoomRevenue;
 @Column(name="unknown_revenue_room_nights",nullable=false) private long unknownRevenueRoomNights;
}
