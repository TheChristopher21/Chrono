package com.chrono.chrono.dto;

public class TaskReportDTO {
    private Long taskId;
    private String taskName;
    private Long totalMinutes;
    private Integer budgetMinutes;
    private Integer remainingMinutes;

    public TaskReportDTO() {}

    public TaskReportDTO(Long taskId, String taskName, Long totalMinutes, Integer budgetMinutes) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.totalMinutes = totalMinutes;
        this.budgetMinutes = budgetMinutes;
        if (budgetMinutes != null) {
            this.remainingMinutes = budgetMinutes - totalMinutes.intValue();
        }
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public Long getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }

    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }

    public Integer getRemainingMinutes() { return remainingMinutes; }
    public void setRemainingMinutes(Integer remainingMinutes) { this.remainingMinutes = remainingMinutes; }
}
