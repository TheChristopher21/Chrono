package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name="pms_revenue_budgets",uniqueConstraints=@UniqueConstraint(name="uk_pms_revenue_budget_month",columnNames={"property_id","month_start"}))
public class PmsRevenueBudget {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private long version;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
 @Column(name="month_start",nullable=false) private LocalDate monthStart;
 @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
 @Column(name="room_nights",nullable=false) private long roomNights;
 @Column(name="net_room_revenue",nullable=false,precision=19,scale=4) private BigDecimal netRoomRevenue;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Column(name="updated_by",nullable=false,length=120) private String updatedBy;
}
