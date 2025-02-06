package com.chrono.chrono.dto;

public class WorkConfigDTO {
    private Integer expectedWorkDays;
    private Double dailyWorkHours;
    private Integer breakDuration;

    public Integer getExpectedWorkDays() {
        return expectedWorkDays;
    }
    public void setExpectedWorkDays(Integer expectedWorkDays) {
        this.expectedWorkDays = expectedWorkDays;
    }
    public Double getDailyWorkHours() {
        return dailyWorkHours;
    }
    public void setDailyWorkHours(Double dailyWorkHours) {
        this.dailyWorkHours = dailyWorkHours;
    }
    public Integer getBreakDuration() {
        return breakDuration;
    }
    public void setBreakDuration(Integer breakDuration) {
        this.breakDuration = breakDuration;
    }
}
