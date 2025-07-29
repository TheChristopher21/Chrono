package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class UserScheduleDTO {
    private Integer scheduleCycle;
    private List<Map<String, Double>> weeklySchedule;
    private LocalDate scheduleEffectiveDate;

    public UserScheduleDTO() {}

    public UserScheduleDTO(Integer scheduleCycle, List<Map<String, Double>> weeklySchedule, LocalDate scheduleEffectiveDate) {
        this.scheduleCycle = scheduleCycle;
        this.weeklySchedule = weeklySchedule;
        this.scheduleEffectiveDate = scheduleEffectiveDate;
    }

    public Integer getScheduleCycle() {
        return scheduleCycle;
    }

    public void setScheduleCycle(Integer scheduleCycle) {
        this.scheduleCycle = scheduleCycle;
    }

    public List<Map<String, Double>> getWeeklySchedule() {
        return weeklySchedule;
    }

    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) {
        this.weeklySchedule = weeklySchedule;
    }

    public LocalDate getScheduleEffectiveDate() {
        return scheduleEffectiveDate;
    }

    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) {
        this.scheduleEffectiveDate = scheduleEffectiveDate;
    }
}
