package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList; // Für Initialisierung von weeklySchedule

@Getter
public class UserDTO {
    // ----- Getter und Setter -----
    private Long id;
    private String username;
    // Für Frontend->Backend Kommunikation (neues PW)
    private String password; // Für Erstellung/Änderung vom Frontend
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;
    private Double expectedWorkDays;
    private Double dailyWorkHours;
    private Integer breakDuration;
    private String color;
    private Integer scheduleCycle;
    private List<Map<String, Double>> weeklySchedule;
    private LocalDate scheduleEffectiveDate;
    // DTO kann null sein, wenn nicht vom Client gesetzt
    private Boolean isHourly;
    private Integer annualVacationDays;
    private Integer trackingBalanceInMinutes;
    // DTO kann null sein
    private Boolean isPercentage;
    // DTO kann null sein
    private Integer workPercentage;
    private Long companyId;

    public UserDTO() {
        // Initialisiere Listen, um NullPointerExceptions zu vermeiden, falls keine Daten vom Frontend kommen
        this.roles = new ArrayList<>();
        this.weeklySchedule = new ArrayList<>();
    }

    // Konstruktor von User-Entität (wird für Antworten vom Backend verwendet)
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        // user.getPassword() hier NICHT zuweisen! DTO sollte kein gehashtes Passwort zurückgeben.
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
        this.weeklySchedule = user.getWeeklySchedule(); // Nimmt die initialisierte Liste oder die gesetzte
        this.scheduleEffectiveDate = user.getScheduleEffectiveDate();
        this.isHourly = user.getIsHourly(); // Verwendet Getter der Entität (gibt false bei null)
        this.annualVacationDays = user.getAnnualVacationDays();
        this.trackingBalanceInMinutes = user.getTrackingBalanceInMinutes(); // Verwendet Getter der Entität (gibt 0 bei null)
        this.isPercentage = user.getIsPercentage(); // Verwendet Getter der Entität (gibt false bei null)
        this.workPercentage = user.getWorkPercentage(); // Verwendet Getter der Entität (gibt 100 bei null)
        this.companyId = (user.getCompany() != null) ? user.getCompany().getId() : null;
    }

    // All-Args-Konstruktor (falls benötigt, z.B. für Tests oder manuelle Erstellung)
    public UserDTO(Long id, String username, String password, String firstName, String lastName, String email, List<String> roles,
                   Double expectedWorkDays, Double dailyWorkHours, Integer breakDuration, String color,
                   Integer scheduleCycle, List<Map<String, Double>> weeklySchedule, LocalDate scheduleEffectiveDate,
                   Boolean isHourly, Integer annualVacationDays, Integer trackingBalanceInMinutes,
                   Boolean isPercentage, Integer workPercentage, Long companyId) {
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
    }


    public void setId(Long id) { this.id = id; }

    public void setUsername(String username) { this.username = username; }

    public void setPassword(String password) { this.password = password; }

    public void setFirstName(String firstName) { this.firstName = firstName; }

    public void setLastName(String lastName) { this.lastName = lastName; }

    public void setEmail(String email) { this.email = email; }

    public void setRoles(List<String> roles) { this.roles = roles; }

    public void setExpectedWorkDays(Double expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }

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
}