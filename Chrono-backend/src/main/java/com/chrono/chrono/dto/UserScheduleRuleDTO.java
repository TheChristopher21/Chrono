package com.chrono.chrono.dto;

import com.chrono.chrono.entities.UserScheduleRule;

import java.time.LocalDate;

public class UserScheduleRuleDTO {

    private Long id;
    private Long userId;
    private String ruleType;           // z.B. "EVERY_2_WEEKS_FRIDAY_OFF"
    private LocalDate startDate;       // ab wann die Regel gilt
    private Integer repeatIntervalDays;
    private Integer dayOfWeek;         // 1=Mo,...5=Fr,...
    private String dayMode;            // z.B. "OFF", "HALF_DAY" etc.

    public UserScheduleRuleDTO() {
    }

    public UserScheduleRuleDTO(UserScheduleRule rule) {
        this.id = rule.getId();
        this.userId = rule.getUser().getId();
        this.ruleType = rule.getRuleType();
        this.startDate = rule.getStartDate();
        this.repeatIntervalDays = rule.getRepeatIntervalDays();
        this.dayOfWeek = rule.getDayOfWeek();
        this.dayMode = rule.getDayMode();
    }

    // ---------------------------
    // Getter & Setter
    // ---------------------------

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRuleType() {
        return ruleType;
    }
    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public Integer getRepeatIntervalDays() {
        return repeatIntervalDays;
    }
    public void setRepeatIntervalDays(Integer repeatIntervalDays) {
        this.repeatIntervalDays = repeatIntervalDays;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }
    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getDayMode() {
        return dayMode;
    }
    public void setDayMode(String dayMode) {
        this.dayMode = dayMode;
    }
}
