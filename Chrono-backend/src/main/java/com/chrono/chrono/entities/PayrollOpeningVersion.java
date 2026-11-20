package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.*;

/** Independent imported year history; never a payslip or an entry in the time account. */
@Entity
@Table(name = "payroll_opening_versions")
public class PayrollOpeningVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, updatable=false) private Long companyId;
    @Column(nullable=false, updatable=false) private Long userId;
    @Column(nullable=false, updatable=false) private LocalDate payrollStart;
    @Column(nullable=false, updatable=false, length=250) private String source;
    @Column(nullable=false, updatable=false) private Long createdBy;
    @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
    @Lob @Column(nullable=false, updatable=false, columnDefinition="longtext") private String parameters;
    @Column(nullable=false) private boolean active=true;
    @Column(nullable=false) private boolean discarded;
    @Column(updatable=false) private Long supersedesOpeningId;
    @Column(length=36) private String correctionBatch;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long value) { companyId=value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId=value; }
    public LocalDate getPayrollStart() { return payrollStart; }
    public void setPayrollStart(LocalDate value) { payrollStart=value; }
    public String getSource() { return source; }
    public void setSource(String value) { source=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public String getParameters() { return parameters; }
    public void setParameters(String value) { parameters=value; }
    public boolean isActive(){return active;} public void setActive(boolean value){active=value;}
    public boolean isDiscarded(){return discarded;} public void setDiscarded(boolean value){discarded=value;}
    public Long getSupersedesOpeningId(){return supersedesOpeningId;} public void setSupersedesOpeningId(Long value){supersedesOpeningId=value;}
    public String getCorrectionBatch(){return correctionBatch;} public void setCorrectionBatch(String value){correctionBatch=value;}
}
