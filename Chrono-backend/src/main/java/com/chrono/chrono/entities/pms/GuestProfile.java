package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_guests", indexes = {
        @Index(name = "idx_pms_guest_company_name", columnList = "company_id,last_name,first_name"),
        @Index(name = "idx_pms_guest_company_email", columnList = "company_id,email")
})
public class GuestProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "reference_code", length = 20)
    private String referenceCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 190)
    private String email;

    @Column(name = "private_email", length = 190)
    private String privateEmail;
    @Column(name = "business_email", length = 190)
    private String businessEmail;
    @Column(name = "additional_emails", columnDefinition = "text")
    private String additionalEmails;
    @Column(name = "billing_profile", columnDefinition = "text")
    private String billingProfile;
    @Column(name = "dietary_notes", length = 1000)
    private String dietaryNotes;
    @Column(name = "vat_number", length = 80)
    private String vatNumber;
    @Column(name = "organization_contact_id", length = 36)
    private String organizationContactId;
    @Column(name = "billing_override", nullable = false)
    private boolean billingOverride;

    @Column(length = 60)
    private String phone;

    @Column(name = "address_line_1", length = 180)
    private String addressLine1;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String city;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "vehicle_plate", length = 40)
    private String vehiclePlate;

    @Column(name = "room_preferences", length = 1000)
    private String roomPreferences;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private PmsOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private GuestProfile mergedInto;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality_code", length = 2)
    private String nationalityCode;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode = "de";

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean vip;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
