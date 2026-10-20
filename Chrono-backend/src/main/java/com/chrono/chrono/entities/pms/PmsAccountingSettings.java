package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity @Getter @Setter @Table(name="pms_accounting_settings")
public class PmsAccountingSettings {
    @Id @Column(name="property_id") private Long propertyId;
    @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
    @ElementCollection @CollectionTable(name="pms_accounting_accounts",joinColumns=@JoinColumn(name="property_id"))
    @MapKeyColumn(name="account_key",length=40) @Column(name="account_code",length=32,nullable=false)
    private Map<String,String> accounts=new LinkedHashMap<>();
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="updated_by",length=120,nullable=false) private String updatedBy;
}
