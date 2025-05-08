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
    private List<String> roles; // Nur Strings ("ROLE_ADMIN", ...)
    private Double expectedWorkDays;
    private Double dailyWorkHours;
    private Integer breakDuration;
    private String color;
    private Integer scheduleCycle;
    private List<Map<String, Double>> weeklySchedule;
    private LocalDate scheduleEffectiveDate;
    private Boolean isHourly;
    private Integer annualVacationDays;
    private Integer trackingBalanceInMinutes;
    private Boolean isPercentage;
    private Integer workPercentage;

    // NEU: Die ID der Firma
    private Long companyId;

    public UserDTO() {}

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        // Hier bilden wir die Rolle-Entities in reine Strings ab:
        this.roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());
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
        this.isPercentage = user.getIsPercentage();
        this.workPercentage = user.getWorkPercentage();

        // NEU: Wenn user.getCompany() != null => companyId = user.getCompany().getId()
        this.companyId = (user.getCompany() != null) ? user.getCompany().getId() : null;
    }

    // Für den Fall, dass du einen kompletten All-Args-Konstruktor brauchst:
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
                   Integer workPercentage,
                   Integer trackingBalanceInMinutes,
                   Long companyId) {
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
        this.trackingBalanceInMinutes = trackingBalanceInMinutes;
        this.companyId = companyId;
    }

    // ----- Getter/Setter -----

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

    public Long getCompanyId() {
        return companyId;
    }
    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }
}
