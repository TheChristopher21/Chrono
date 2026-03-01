package com.chrono.chrono.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProjectHierarchyNodeDTO {
    private Long id;
    private String name;
    private String customerName;
    private Long parentId;
    private Integer budgetMinutes;
    private BigDecimal hourlyRate;
    private Long totalMinutes;
    private Long billableMinutes;
    private Double utilization;
    private List<ProjectHierarchyNodeDTO> children = new ArrayList<>();

    public ProjectHierarchyNodeDTO() {
    }

    public ProjectHierarchyNodeDTO(Long id, String name, String customerName, Long parentId, Integer budgetMinutes, BigDecimal hourlyRate) {
        this.id = id;
        this.name = name;
        this.customerName = customerName;
        this.parentId = parentId;
        this.budgetMinutes = budgetMinutes;
        this.hourlyRate = hourlyRate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getBudgetMinutes() {
        return budgetMinutes;
    }

    public void setBudgetMinutes(Integer budgetMinutes) {
        this.budgetMinutes = budgetMinutes;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public Long getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(Long totalMinutes) {
        this.totalMinutes = totalMinutes;
        updateUtilization();
    }

    public Long getBillableMinutes() {
        return billableMinutes;
    }

    public void setBillableMinutes(Long billableMinutes) {
        this.billableMinutes = billableMinutes;
    }

    public Double getUtilization() {
        return utilization;
    }

    public void setUtilization(Double utilization) {
        this.utilization = utilization;
    }

    public List<ProjectHierarchyNodeDTO> getChildren() {
        return children;
    }

    public void setChildren(List<ProjectHierarchyNodeDTO> children) {
        this.children = children;
    }

    public void addChild(ProjectHierarchyNodeDTO child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    public void updateUtilization() {
        if (budgetMinutes != null && budgetMinutes > 0 && totalMinutes != null) {
            this.utilization = (double) totalMinutes / (double) budgetMinutes;
        }
    }
}
