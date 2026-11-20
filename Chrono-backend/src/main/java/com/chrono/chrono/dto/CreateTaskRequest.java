package com.chrono.chrono.dto;

public class CreateTaskRequest extends CreateRequest {
    private String name;
    private Reference project;
    private Boolean billable;
    private Integer budgetMinutes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Reference getProject() { return project; }
    public void setProject(Reference project) { this.project = project; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }
}
