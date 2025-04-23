// src/main/java/com/chrono/chrono/dto/UserDTO.java
package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;
    private Double expectedWorkDays;        // z. B. 9.5
    private Double dailyWorkHours;
    private Integer breakDuration;
    private String color;
    private Integer scheduleCycle;
    // Hier <String, Double> => Kommazahlen pro Tag
    private List<Map<String, Double>> weeklySchedule;
    private LocalDate scheduleEffectiveDate;
    private Boolean isHourly;
    private Integer annualVacationDays;
    private Integer trackingBalanceInMinutes;

    // NEU: Felder für Prozent-basierte User
    private Boolean isPercentage;
    private Integer workPercentage;

    public UserDTO() {}

    // Wichtig: im Konstruktor die neuen Felder übernehmen.
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.roles = user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList());
        this.expectedWorkDays = user.getExpectedWorkDays();
        this.dailyWorkHours = user.getDailyWorkHours();
        this.breakDuration = user.getBreakDuration();
        this.color = user.getColor();
        this.scheduleCycle = user.getScheduleCycle();
        this.weeklySchedule = user.getWeeklySchedule();
        this.scheduleEffectiveDate = user.getScheduleEffectiveDate();
        this.isHourly = user.getIsHourly();
        this.annualVacationDays = user.getAnnualVacationDays();
        this.trackingBalanceInMinutes = user.getTrackingBalanceInMinutes();

        // NEU hinzugefügt:
        this.isPercentage = user.getIsPercentage();
        this.workPercentage = user.getWorkPercentage();
    }

    public UserDTO(Long id,
                   String username,
                   String firstName,
                   String lastName,
                   String email,
                   List<String> roles,
                   Double expectedWorkDays,
                   Double dailyWorkHours,
                   Integer breakDuration,
                   String color,
                   Integer scheduleCycle,
                   List<Map<String, Double>> weeklySchedule,
                   LocalDate scheduleEffectiveDate,
                   Boolean isHourly,
                   Integer annualVacationDays,
                   Boolean isPercentage,
                   Integer workPercentage) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roles = roles;
        this.expectedWorkDays = expectedWorkDays;
        this.dailyWorkHours = dailyWorkHours;
        this.breakDuration = breakDuration;
        this.color = color;
        this.scheduleCycle = scheduleCycle;
        this.weeklySchedule = weeklySchedule;
        this.scheduleEffectiveDate = scheduleEffectiveDate;
        this.isHourly = isHourly;
        this.annualVacationDays = annualVacationDays;
        this.isPercentage = isPercentage;
        this.workPercentage = workPercentage;
    }

    // ---------------------- Getter & Setter ----------------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public Double getExpectedWorkDays() { return expectedWorkDays; }
    public void setExpectedWorkDays(Double expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }

    public Double getDailyWorkHours() { return dailyWorkHours; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }

    public Integer getBreakDuration() { return breakDuration; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getScheduleCycle() { return scheduleCycle; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }

    public List<Map<String, Double>> getWeeklySchedule() { return weeklySchedule; }
    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }

    public Boolean getIsHourly() { return isHourly; }
    public void setIsHourly(Boolean isHourly) { this.isHourly = isHourly; }

    public Integer getAnnualVacationDays() { return annualVacationDays; }
    public void setAnnualVacationDays(Integer annualVacationDays) { this.annualVacationDays = annualVacationDays; }

    // NEU: isPercentage & workPercentage
    public Boolean getIsPercentage() { return isPercentage; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = isPercentage; }

    public Integer getWorkPercentage() { return workPercentage; }
    public void setWorkPercentage(Integer workPercentage) { this.workPercentage = workPercentage; }
    public Integer getTrackingBalanceInMinutes() {
        return trackingBalanceInMinutes;
    }

    public void setTrackingBalanceInMinutes(Integer trackingBalanceInMinutes) {
        this.trackingBalanceInMinutes = trackingBalanceInMinutes;
    }

}
