package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class UserDTO {
    // ----- Fields -----
    private Long id;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;
    private Integer expectedWorkDays;
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
    private Long companyId;
    private String companyCantonAbbreviation;
    private Boolean customerTrackingEnabled; // Kept
    private Long lastCustomerId;
    private String lastCustomerName;

    public UserDTO() {
        this.roles = new ArrayList<>();
        this.weeklySchedule = new ArrayList<>();
    }

    // Constructor from User entity
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
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
        this.companyId = (user.getCompany() != null) ? user.getCompany().getId() : null;
        this.companyCantonAbbreviation = (user.getCompany() != null) ? user.getCompany().getCantonAbbreviation() : null;
        this.lastCustomerId = user.getLastCustomer() != null ? user.getLastCustomer().getId() : null;
        this.lastCustomerName = user.getLastCustomer() != null ? user.getLastCustomer().getName() : null;
        this.customerTrackingEnabled = (user.getCompany() != null) ? user.getCompany().getCustomerTrackingEnabled() : null; // Kept
    }

    // All-Args-Constructor
    public UserDTO(Long id, String username, String password, String firstName, String lastName, String email, List<String> roles,
                   Integer expectedWorkDays, Double dailyWorkHours, Integer breakDuration, String color,
                   Integer scheduleCycle, List<Map<String, Double>> weeklySchedule, LocalDate scheduleEffectiveDate,
                   Boolean isHourly, Integer annualVacationDays, Integer trackingBalanceInMinutes,
                   Boolean isPercentage, Integer workPercentage, Long companyId,
                   Long lastCustomerId, String lastCustomerName, Boolean customerTrackingEnabled) { // Kept
        this.id = id;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roles = roles != null ? roles : new ArrayList<>();
        this.expectedWorkDays = expectedWorkDays;
        this.dailyWorkHours = dailyWorkHours;
        this.breakDuration = breakDuration;
        this.color = color;
        this.scheduleCycle = scheduleCycle;
        this.weeklySchedule = weeklySchedule != null ? weeklySchedule : new ArrayList<>();
        this.scheduleEffectiveDate = scheduleEffectiveDate;
        this.isHourly = isHourly != null ? isHourly : false;
        this.annualVacationDays = annualVacationDays;
        this.trackingBalanceInMinutes = trackingBalanceInMinutes != null ? trackingBalanceInMinutes : 0;
        this.isPercentage = isPercentage != null ? isPercentage : false;
        this.workPercentage = workPercentage != null ? workPercentage : 100;
        this.companyId = companyId;
        this.lastCustomerId = lastCustomerId;
        this.lastCustomerName = lastCustomerName;
        this.customerTrackingEnabled = customerTrackingEnabled; // Kept
    }

    // ----- Getters -----
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
    public Integer getExpectedWorkDays() { return expectedWorkDays; }
    public Double getDailyWorkHours() { return dailyWorkHours; }
    public Integer getBreakDuration() { return breakDuration; }
    public String getColor() { return color; }
    public Integer getScheduleCycle() { return scheduleCycle; }
    public List<Map<String, Double>> getWeeklySchedule() { return weeklySchedule; }
    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public Boolean getIsHourly() { return isHourly; }
    public Integer getAnnualVacationDays() { return annualVacationDays; }
    public Integer getTrackingBalanceInMinutes() { return trackingBalanceInMinutes; }
    public Boolean getIsPercentage() { return isPercentage; }
    public Integer getWorkPercentage() { return workPercentage; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyCantonAbbreviation() { return companyCantonAbbreviation; }
    public Long getLastCustomerId() { return lastCustomerId; }
    public String getLastCustomerName() { return lastCustomerName; }
    public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; } // Kept

    // ----- Setters -----
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }
    public void setColor(String color) { this.color = color; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }
    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }
    public void setIsHourly(Boolean isHourly) { this.isHourly = isHourly; }
    public void setAnnualVacationDays(Integer annualVacationDays) { this.annualVacationDays = annualVacationDays; }
    public void setTrackingBalanceInMinutes(Integer trackingBalanceInMinutes) { this.trackingBalanceInMinutes = trackingBalanceInMinutes; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = isPercentage; }
    public void setWorkPercentage(Integer workPercentage) { this.workPercentage = workPercentage; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setCompanyCantonAbbreviation(String companyCantonAbbreviation) { this.companyCantonAbbreviation = companyCantonAbbreviation; }
    public void setLastCustomerId(Long lastCustomerId) { this.lastCustomerId = lastCustomerId; }
    public void setLastCustomerName(String lastCustomerName) { this.lastCustomerName = lastCustomerName; }
    public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; } // Kept
}