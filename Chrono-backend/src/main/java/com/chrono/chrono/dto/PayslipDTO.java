package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.PayComponent;
import java.time.LocalDate;
import java.util.List;

public class PayslipDTO {
    private Long id;
    private Long userId;
    private String firstName;
    private String lastName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Double grossSalary;
    private Double deductions;
    private Double netSalary;
    private Double allowances;
    private Double bonuses;
    private Double oneTimePayments;
    private Double taxFreeAllowances;
    private List<PayComponent> earnings;
    private List<PayComponent> deductionsList;
    private Double employerContributions;
    private LocalDate payoutDate;
    private String bankAccount;
    private String socialSecurityNumber;
    private String payType;
    private boolean locked;
    private String pdfPath;
    private Integer version;
    private boolean approved;

    public PayslipDTO(Payslip ps) {
        this.id = ps.getId();
        this.userId = ps.getUser() != null ? ps.getUser().getId() : null;
        this.firstName = ps.getUser() != null ? ps.getUser().getFirstName() : null;
        this.lastName = ps.getUser() != null ? ps.getUser().getLastName() : null;
        this.periodStart = ps.getPeriodStart();
        this.periodEnd = ps.getPeriodEnd();
        this.grossSalary = ps.getGrossSalary();
        this.deductions = ps.getDeductions();
        this.netSalary = ps.getNetSalary();
        this.allowances = ps.getAllowances();
        this.bonuses = ps.getBonuses();
        this.oneTimePayments = ps.getOneTimePayments();
        this.taxFreeAllowances = ps.getTaxFreeAllowances();
        this.earnings = ps.getEarnings();
        this.deductionsList = ps.getDeductionsList();
        this.employerContributions = ps.getEmployerContributions();
        this.payoutDate = ps.getPayoutDate();
        this.bankAccount = ps.getBankAccount();
        this.socialSecurityNumber = ps.getSocialSecurityNumber();
        this.payType = ps.getPayType();
        this.locked = ps.isLocked();
        this.pdfPath = ps.getPdfPath();
        this.version = ps.getVersion();
        this.approved = ps.isApproved();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Double getGrossSalary() { return grossSalary; }
    public Double getDeductions() { return deductions; }
    public Double getNetSalary() { return netSalary; }
    public Double getAllowances() { return allowances; }
    public Double getBonuses() { return bonuses; }
    public Double getOneTimePayments() { return oneTimePayments; }
    public Double getTaxFreeAllowances() { return taxFreeAllowances; }
    public List<PayComponent> getEarnings() { return earnings; }
    public List<PayComponent> getDeductionsList() { return deductionsList; }
    public Double getEmployerContributions() { return employerContributions; }
    public LocalDate getPayoutDate() { return payoutDate; }
    public String getBankAccount() { return bankAccount; }
    public String getSocialSecurityNumber() { return socialSecurityNumber; }
    public String getPayType() { return payType; }
    public boolean isLocked() { return locked; }
    public String getPdfPath() { return pdfPath; }
    public Integer getVersion() { return version; }
    public boolean isApproved() { return approved; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
