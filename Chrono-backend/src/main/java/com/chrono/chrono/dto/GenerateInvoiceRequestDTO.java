package com.chrono.chrono.dto;

import java.math.BigDecimal;

public class GenerateInvoiceRequestDTO {
    private Long projectId;
    private String startDate;
    private String endDate;
    private boolean includeChildren;
    private BigDecimal overrideRate;
    private String currency;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public boolean isIncludeChildren() {
        return includeChildren;
    }

    public void setIncludeChildren(boolean includeChildren) {
        this.includeChildren = includeChildren;
    }

    public BigDecimal getOverrideRate() {
        return overrideRate;
    }

    public void setOverrideRate(BigDecimal overrideRate) {
        this.overrideRate = overrideRate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
