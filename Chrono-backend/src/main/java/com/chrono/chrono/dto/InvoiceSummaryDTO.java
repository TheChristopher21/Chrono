package com.chrono.chrono.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvoiceSummaryDTO {
    private Long projectId;
    private String projectName;
    private String customerName;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean includeChildren;
    private BigDecimal hourlyRate;
    private BigDecimal overrideRate;
    private long totalBillableMinutes;
    private double totalBillableHours;
    private BigDecimal totalAmount;
    private List<InvoiceLineDTO> lineItems = new ArrayList<>();
    private String currency;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isIncludeChildren() {
        return includeChildren;
    }

    public void setIncludeChildren(boolean includeChildren) {
        this.includeChildren = includeChildren;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getOverrideRate() {
        return overrideRate;
    }

    public void setOverrideRate(BigDecimal overrideRate) {
        this.overrideRate = overrideRate;
    }

    public long getTotalBillableMinutes() {
        return totalBillableMinutes;
    }

    public void setTotalBillableMinutes(long totalBillableMinutes) {
        this.totalBillableMinutes = totalBillableMinutes;
        this.totalBillableHours = totalBillableMinutes / 60.0;
    }

    public double getTotalBillableHours() {
        return totalBillableHours;
    }

    public void setTotalBillableHours(double totalBillableHours) {
        this.totalBillableHours = totalBillableHours;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<InvoiceLineDTO> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<InvoiceLineDTO> lineItems) {
        this.lineItems = lineItems;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
