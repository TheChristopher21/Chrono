package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_organizations", indexes = @Index(
        name = "idx_pms_organization_company_name",
        columnList = "company_id,name"
))
public class PmsOrganization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationType type;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "vat_number", length = 40)
    private String vatNumber;

    @Column(name = "address_line_1", length = 180)
    private String addressLine1;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "CH";

    @Column(length = 190)
    private String email;

    @Column(length = 60)
    private String phone;

    @Column(name = "billing_email", length = 190)
    private String billingEmail;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 10;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
