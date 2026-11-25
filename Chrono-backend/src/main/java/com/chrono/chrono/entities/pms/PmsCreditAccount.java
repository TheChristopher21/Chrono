package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter
@Table(name="pms_credit_accounts", uniqueConstraints=@UniqueConstraint(name="uk_pms_credit_account_property_org", columnNames={"property_id","organization_id"}))
public class PmsCreditAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="property_id", nullable=false) private HotelProperty property;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="organization_id", nullable=false) private PmsOrganization organization;
    @Column(nullable=false) private boolean enabled;
    @Column(name="credit_limit", nullable=false, precision = 19, scale = 4) private BigDecimal creditLimit=BigDecimal.ZERO;
    @Column(name="payment_terms_days", nullable=false) private int paymentTermsDays=30;
}
