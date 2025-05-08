package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import com.chrono.chrono.converters.WeeklyScheduleConverter;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;

    @Column(name = "admin_password")
    private String adminPassword;

    private String firstName;
    private String lastName;
    private String email;
    // ganz oben bei den anderen Feldern
    private Integer trackingBalanceInMinutes = 0;
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<VacationRequest> vacationRequests = new HashSet<>();
    @ManyToOne
    @JoinColumn(name = "company_id")
    @JsonBackReference
    private Company company;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<TimeTracking> timeTracks = new HashSet<>();

    @Column(name = "expected_work_days")
    private Double expectedWorkDays;

    @Column(name = "daily_work_hours")
    private Double dailyWorkHours;

    @Column(name = "break_duration")
    private Integer breakDuration;

    @Column(name = "color")
    private String color;

    @Column(name = "annual_vacation_days")
    private Integer annualVacationDays;

    @Column(name = "schedule_cycle")
    private Integer scheduleCycle;

    @Lob
    @Convert(converter = WeeklyScheduleConverter.class)
    @Column(name = "weekly_schedule")
    private List<Map<String, Double>> weeklySchedule;

    @Column(name = "schedule_effective_date")
    private LocalDate scheduleEffectiveDate;

    @Column(name = "is_hourly")
    private Boolean isHourly;

    // Neues Feld: ob der User percentage-basiert arbeitet
    @Column(name = "is_percentage")
    private Boolean isPercentage = false;

    // Neues Feld: Der Prozentwert 0..100
    @Column(name = "work_percentage")
    private Integer workPercentage = 100;

    public User() {}

    // ------------------- Getter/Setter -------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Set<TimeTracking> getTimeTracks() { return timeTracks; }
    public void setTimeTracks(Set<TimeTracking> timeTracks) { this.timeTracks = timeTracks; }

    public Double getExpectedWorkDays() { return expectedWorkDays; }
    public void setExpectedWorkDays(Double expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }

    public Double getDailyWorkHours() { return dailyWorkHours; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }

    public Integer getBreakDuration() { return breakDuration; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getAnnualVacationDays() { return annualVacationDays; }
    public void setAnnualVacationDays(Integer annualVacationDays) { this.annualVacationDays = annualVacationDays; }

    public Integer getScheduleCycle() { return scheduleCycle; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }

    public List<Map<String, Double>> getWeeklySchedule() { return weeklySchedule; }
    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }

    public Boolean getIsHourly() { return isHourly != null ? isHourly : false; }
    public boolean isHourly()   { return getIsHourly(); }
    public void setIsHourly(Boolean isHourly) { this.isHourly = isHourly; }

    public Boolean getIsPercentage() { return isPercentage != null ? isPercentage : false; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = isPercentage; }
    public Integer getTrackingBalanceInMinutes() {
        return trackingBalanceInMinutes;
    }

    public void setTrackingBalanceInMinutes(Integer trackingBalanceInMinutes) {
        this.trackingBalanceInMinutes = trackingBalanceInMinutes;
    }
    public Set<VacationRequest> getVacationRequests() {
        return vacationRequests;
    }
    public void setVacationRequests(Set<VacationRequest> vacationRequests) {
        this.vacationRequests = vacationRequests;
    }
    public Company getCompany() {
        return company;
    }
    public void setCompany(Company company) {
        this.company = company;
    }
    public Integer getWorkPercentage() {
        return workPercentage != null ? workPercentage : 100;
    }
    public void setWorkPercentage(Integer workPercentage) {
        if (workPercentage == null) {
            this.workPercentage = 100;
        } else {
            this.workPercentage = workPercentage;
        }
    }
}
