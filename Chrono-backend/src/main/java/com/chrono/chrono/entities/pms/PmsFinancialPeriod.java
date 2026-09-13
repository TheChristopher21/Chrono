package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "pms_financial_periods")
public class PmsFinancialPeriod {
    @Id
    private Long propertyId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "property_id")
    private HotelProperty property;
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;
    @Column(name = "last_closed_date")
    private LocalDate lastClosedDate;
}
