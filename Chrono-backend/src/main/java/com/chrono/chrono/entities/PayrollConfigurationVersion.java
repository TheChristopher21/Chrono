package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Append-only contractual inputs. A null userId denotes company insurance rates. */
@Entity
@Table(name = "payroll_configuration_versions")
public class PayrollConfigurationVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Long companyId;
    @Column(updatable = false)
    private Long userId;
    @Column(nullable = false, updatable = false)
    private LocalDate effectiveFrom;
    @Column(nullable = false, length = 250, updatable = false)
    private String source;
    @Column(nullable = false, updatable = false)
    private Long createdBy;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal uvgBuRate;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal uvgNbuRate;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal ktgRateEmployee;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal ktgRateEmployer;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal fakRate;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal fakEmployeeRate;
    @Column(precision = 11, scale = 8, updatable = false)
    private BigDecimal ahvAdminRate;
    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal ktgAnnualSalaryCap;
    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal bvgEmployee;
    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal bvgEmployer;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long value) { this.companyId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate value) { this.effectiveFrom = value; }
    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { this.createdBy = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public BigDecimal getUvgBuRate() { return uvgBuRate; }
    public void setUvgBuRate(BigDecimal value) { this.uvgBuRate = value; }
    public BigDecimal getUvgNbuRate() { return uvgNbuRate; }
    public void setUvgNbuRate(BigDecimal value) { this.uvgNbuRate = value; }
    public BigDecimal getKtgRateEmployee() { return ktgRateEmployee; }
    public void setKtgRateEmployee(BigDecimal value) { this.ktgRateEmployee = value; }
    public BigDecimal getKtgRateEmployer() { return ktgRateEmployer; }
    public void setKtgRateEmployer(BigDecimal value) { this.ktgRateEmployer = value; }
    public BigDecimal getFakRate() { return fakRate; }
    public void setFakRate(BigDecimal value) { this.fakRate = value; }
    public BigDecimal getBvgEmployee() { return bvgEmployee; }
    public BigDecimal getFakEmployeeRate() { return fakEmployeeRate; }
    public void setFakEmployeeRate(BigDecimal v) { fakEmployeeRate = v; }
    public BigDecimal getAhvAdminRate() { return ahvAdminRate; }
    public void setAhvAdminRate(BigDecimal v) { ahvAdminRate = v; }
    public BigDecimal getKtgAnnualSalaryCap() { return ktgAnnualSalaryCap; }
    public void setKtgAnnualSalaryCap(BigDecimal v) { ktgAnnualSalaryCap = v; }
    public void setBvgEmployee(BigDecimal value) { this.bvgEmployee = value; }
    public BigDecimal getBvgEmployer() { return bvgEmployer; }
    public void setBvgEmployer(BigDecimal value) { this.bvgEmployer = value; }
}

