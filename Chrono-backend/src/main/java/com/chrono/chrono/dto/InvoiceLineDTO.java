package com.chrono.chrono.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceLineDTO {
    private Long projectId;
    private String projectName;
    private Long taskId;
    private String taskName;
    private long minutes;
    private double hours;
    private BigDecimal amount;

    public InvoiceLineDTO() {
    }

    public InvoiceLineDTO(Long projectId, String projectName, Long taskId, String taskName, long minutes, BigDecimal rate) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.taskId = taskId;
        this.taskName = taskName;
        this.minutes = minutes;
        this.hours = minutes / 60.0;
        recalcAmount(rate);
    }

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public long getMinutes() {
        return minutes;
    }

    public void setMinutes(long minutes) {
        this.minutes = minutes;
        this.hours = minutes / 60.0;
    }

    public double getHours() {
        return hours;
    }

    public void setHours(double hours) {
        this.hours = hours;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void recalcAmount(BigDecimal rate) {
        BigDecimal effectiveRate = rate != null ? rate : BigDecimal.ZERO;
        BigDecimal minutesDecimal = BigDecimal.valueOf(this.minutes);
        BigDecimal hoursDecimal = minutesDecimal.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        this.amount = hoursDecimal.multiply(effectiveRate);
    }
}
