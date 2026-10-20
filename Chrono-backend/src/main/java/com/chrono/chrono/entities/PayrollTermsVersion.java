package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_terms_versions")
public class PayrollTermsVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long companyId;
    @Column(nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, updatable = false) private LocalDate effectiveFrom;
    @Column(nullable = false, updatable = false, length = 250) private String source;
    @Column(nullable = false, updatable = false) private Long createdBy;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Lob @Column(nullable = false, updatable = false, columnDefinition = "longtext") private String parameters;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { companyId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { effectiveFrom = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public String getParameters() { return parameters; }
    public void setParameters(String v) { parameters = v; }
}
