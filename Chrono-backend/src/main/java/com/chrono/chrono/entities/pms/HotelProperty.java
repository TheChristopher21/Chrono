package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "pms_properties",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_property_company_code",
                columnNames = {"company_id", "code"}
        ),
        indexes = @Index(name = "idx_pms_property_company", columnList = "company_id")
)
public class HotelProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "legal_name", length = 180)
    private String legalName;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "CH";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "CHF";

    @Column(nullable = false, length = 64)
    private String timezone = "Europe/Zurich";

    @Column(name = "address_line_1", length = 180)
    private String addressLine1;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 120)
    private String city;

    @Column(length = 60)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime = LocalTime.of(15, 0);

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime = LocalTime.of(11, 0);

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "tax_number", length = 80)
    private String taxNumber;

    @Column(name = "tax_registration_label", length = 40, nullable = false)
    private String taxRegistrationLabel = "VAT / Tax ID";

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "address_line_2", length = 180)
    private String addressLine2;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "invoice_footer", length = 2000)
    private String invoiceFooter;

    @Column(name = "invoice_prefix", length = 24, nullable = false)
    private String invoicePrefix = "INV";

    @Column(name = "invoice_due_days", nullable = false)
    private int invoiceDueDays = 14;

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

    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String value) { this.taxNumber = value; }

    public String getTaxRegistrationLabel() { return taxRegistrationLabel; }
    public void setTaxRegistrationLabel(String value) { this.taxRegistrationLabel = value; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String value) { this.registrationNumber = value; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String value) { this.addressLine2 = value; }

    public String getRegion() { return region; }
    public void setRegion(String value) { this.region = value; }

    public String getInvoiceFooter() { return invoiceFooter; }
    public void setInvoiceFooter(String value) { this.invoiceFooter = value; }

    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String value) { this.invoicePrefix = value; }

    public int getInvoiceDueDays() { return invoiceDueDays; }
    public void setInvoiceDueDays(int value) { this.invoiceDueDays = value; }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
