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

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 190)
    private String email;

    @Column(length = 60)
    private String phone;

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
