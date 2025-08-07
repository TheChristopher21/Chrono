package com.chrono.chrono.dto;

import java.util.List;

public class ProjectReportDTO {
    private Long projectId;
    private String projectName;
    private Long totalMinutes;
    private Integer budgetMinutes;
    private Integer remainingMinutes;
    private List<TaskReportDTO> tasks;

    public ProjectReportDTO() {}

    public ProjectReportDTO(Long projectId, String projectName, Long totalMinutes, Integer budgetMinutes, List<TaskReportDTO> tasks) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.totalMinutes = totalMinutes;
        this.budgetMinutes = budgetMinutes;
        this.tasks = tasks;
        if (budgetMinutes != null) {
            this.remainingMinutes = budgetMinutes - totalMinutes.intValue();
        }
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public Long getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }

    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }

    public Integer getRemainingMinutes() { return remainingMinutes; }
    public void setRemainingMinutes(Integer remainingMinutes) { this.remainingMinutes = remainingMinutes; }

    public List<TaskReportDTO> getTasks() { return tasks; }
    public void setTasks(List<TaskReportDTO> tasks) { this.tasks = tasks; }
}
