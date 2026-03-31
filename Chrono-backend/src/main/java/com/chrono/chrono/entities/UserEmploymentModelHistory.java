package com.chrono.chrono.entities;

import com.chrono.chrono.converters.WeeklyScheduleConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "user_employment_model_history")
public class UserEmploymentModelHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 20)
    private EmploymentModelType modelType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "work_percentage")
    private Integer workPercentage;

    @Column(name = "expected_work_days")
    private Integer expectedWorkDays;

    @Column(name = "daily_work_hours")
    private Double dailyWorkHours;

    @Column(name = "schedule_cycle")
    private Integer scheduleCycle;

    @Column(name = "schedule_effective_date")
    private LocalDate scheduleEffectiveDate;

    @Lob
    @Convert(converter = WeeklyScheduleConverter.class)
    @Column(name = "weekly_schedule", columnDefinition = "TEXT")
    private List<Map<String, Double>> weeklySchedule = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public EmploymentModelType getModelType() { return modelType; }
    public void setModelType(EmploymentModelType modelType) { this.modelType = modelType; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Integer getWorkPercentage() { return workPercentage; }
    public void setWorkPercentage(Integer workPercentage) { this.workPercentage = workPercentage; }
    public Integer getExpectedWorkDays() { return expectedWorkDays; }
    public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }
    public Double getDailyWorkHours() { return dailyWorkHours; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }
    public Integer getScheduleCycle() { return scheduleCycle; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }
    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }

    public List<Map<String, Double>> getWeeklySchedule() {
        return weeklySchedule != null ? weeklySchedule : new ArrayList<>();
    }

    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) {
        this.weeklySchedule = weeklySchedule;
    }
}
