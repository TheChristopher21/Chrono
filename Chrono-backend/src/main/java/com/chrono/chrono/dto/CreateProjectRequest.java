package com.chrono.chrono.dto;

import java.math.BigDecimal;

public class CreateProjectRequest extends CreateRequest {
    private String name;
    private Reference customer;
    private Reference parent;
    private Integer budgetMinutes;
    private BigDecimal hourlyRate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Reference getCustomer() { return customer; }
    public void setCustomer(Reference customer) { this.customer = customer; }
    public Reference getParent() { return parent; }
    public void setParent(Reference parent) { this.parent = parent; }
    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
}
