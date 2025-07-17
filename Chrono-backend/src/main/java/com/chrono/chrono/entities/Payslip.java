package com.chrono.chrono.entities;

import jakarta.persistence.*;
import jakarta.persistence.Convert;
import java.time.LocalDate;

@Entity
@Table(name = "payslips")
public class Payslip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Double grossSalary;
    private Double deductions;
    private Double netSalary;
    private Double allowances;
    private Double bonuses;
    private Double oneTimePayments;
    private Double taxFreeAllowances;

    @Convert(converter = com.chrono.chrono.utils.EncryptionConverter.class)
    private String bankAccount;
    @Convert(converter = com.chrono.chrono.utils.EncryptionConverter.class)
    private String socialSecurityNumber;

    private String payType;
    private boolean locked = false;
    private String pdfPath;

    @Version
    private Integer version;

    @Column(name = "approved", nullable = false)
    private boolean approved = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public Double getGrossSalary() { return grossSalary; }
    public void setGrossSalary(Double grossSalary) { this.grossSalary = grossSalary; }

    public Double getDeductions() { return deductions; }
    public void setDeductions(Double deductions) { this.deductions = deductions; }

    public Double getNetSalary() { return netSalary; }
    public void setNetSalary(Double netSalary) { this.netSalary = netSalary; }

    public Double getAllowances() { return allowances; }
    public void setAllowances(Double allowances) { this.allowances = allowances; }

    public Double getBonuses() { return bonuses; }
    public void setBonuses(Double bonuses) { this.bonuses = bonuses; }

    public Double getOneTimePayments() { return oneTimePayments; }
    public void setOneTimePayments(Double oneTimePayments) { this.oneTimePayments = oneTimePayments; }

    public Double getTaxFreeAllowances() { return taxFreeAllowances; }
    public void setTaxFreeAllowances(Double taxFreeAllowances) { this.taxFreeAllowances = taxFreeAllowances; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getSocialSecurityNumber() { return socialSecurityNumber; }
    public void setSocialSecurityNumber(String socialSecurityNumber) { this.socialSecurityNumber = socialSecurityNumber; }

    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
}
