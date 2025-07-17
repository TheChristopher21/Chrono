package com.chrono.chrono.entities;

import com.chrono.chrono.dto.CorrectionRequest;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference; // Für bidirektionale Beziehungen, falls benötigt
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap; // Import für HashMap
import java.util.Set;
import java.util.List;
import java.util.ArrayList; // Import für ArrayList
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.converters.WeeklyScheduleConverter;
import jakarta.persistence.Convert;

@Entity
@Table(name = "users")
@EntityListeners(com.chrono.chrono.entities.listeners.UserAuditListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "admin_password")
    private String adminPassword;

    private String firstName;
    private String lastName;

    @Column(unique = true) // E-Mail sollte normalerweise eindeutig sein
    private String email;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;


    @Convert(converter = com.chrono.chrono.utils.EncryptionConverter.class)
    private String bankAccount;

    @Convert(converter = com.chrono.chrono.utils.EncryptionConverter.class)
    private String socialSecurityNumber;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "opt_out", nullable = false)
    private boolean optOut = false;

    @Column(nullable = false)
    private Integer trackingBalanceInMinutes = 0; // Default-Wert direkt hier

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("user-vacationRequests") // Verhindert Rekursion
    private Set<VacationRequest> vacationRequests = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY) // Lazy Fetching für Company ist oft sinnvoll
    @JoinColumn(name = "company_id")
    @JsonBackReference("company-users")
    private Company company;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    // NEU: Beziehung zu TimeTrackingEntry
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("user-timeTrackingEntries")
    private List<TimeTrackingEntry> timeTrackingEntries = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_customer_id")

    private Customer lastCustomer;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("user-correctionRequests") // Verhindert Rekursion
    private Set<CorrectionRequest> correctionRequests = new HashSet<>();

    @Column(name = "expected_work_days")
    private Integer expectedWorkDays; // MODIFIED: Changed from Double to Integer

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
    @Column(name = "weekly_schedule", columnDefinition = "TEXT")
    private List<Map<String, Double>> weeklySchedule = new ArrayList<>(); // Initialisieren, um NullPointer zu vermeiden

    @Column(name = "schedule_effective_date")
    private LocalDate scheduleEffectiveDate;

    @Column(name = "is_hourly", nullable = false)
    private Boolean isHourly = false;

    @Column(name = "is_percentage", nullable = false)
    private Boolean isPercentage = false;

    @Column(name = "work_percentage") // Standardwert 100 ist hier nicht nötig, wird im Getter gehandhabt
    private Integer workPercentage;

    @Column(name = "hourly_rate")
    private Double hourlyRate;

    public User() {}

    public static Map<String, Double> getDefaultWeeklyScheduleMap() {
        Map<String, Double> defaultSchedule = new HashMap<>();
        defaultSchedule.put("monday", 8.5); // Standardwert, anpassbar
        defaultSchedule.put("tuesday", 8.5);
        defaultSchedule.put("wednesday", 8.5);
        defaultSchedule.put("thursday", 8.5);
        defaultSchedule.put("friday", 8.5);
        defaultSchedule.put("saturday", 0.0);
        defaultSchedule.put("sunday", 0.0);
        return defaultSchedule;
    }

    // --- Getter und Setter ---

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

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getSocialSecurityNumber() { return socialSecurityNumber; }
    public void setSocialSecurityNumber(String socialSecurityNumber) { this.socialSecurityNumber = socialSecurityNumber; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public boolean isOptOut() { return optOut; }
    public void setOptOut(boolean optOut) { this.optOut = optOut; }

    public Integer getTrackingBalanceInMinutes() {
        return trackingBalanceInMinutes != null ? trackingBalanceInMinutes : 0;
    }
    public void setTrackingBalanceInMinutes(Integer trackingBalanceInMinutes) {
        this.trackingBalanceInMinutes = (trackingBalanceInMinutes != null ? trackingBalanceInMinutes : 0);
    }

    public Set<VacationRequest> getVacationRequests() { return vacationRequests; }
    public void setVacationRequests(Set<VacationRequest> vacationRequests) { this.vacationRequests = vacationRequests; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public List<TimeTrackingEntry> getTimeTrackingEntries() { return timeTrackingEntries; }
    public void setTimeTrackingEntries(List<TimeTrackingEntry> timeTrackingEntries) { this.timeTrackingEntries = timeTrackingEntries; }
    public Customer getLastCustomer() { return lastCustomer; }
    public void setLastCustomer(Customer lastCustomer) { this.lastCustomer = lastCustomer; }

    public Set<CorrectionRequest> getCorrectionRequests() { return correctionRequests; }
    public void setCorrectionRequests(Set<CorrectionRequest> correctionRequests) { this.correctionRequests = correctionRequests; }

    // MODIFIED: Getter for Integer
    public Integer getExpectedWorkDays() { return expectedWorkDays; }
    // MODIFIED: Setter for Integer
    public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }

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

    public List<Map<String, Double>> getWeeklySchedule() {
        return weeklySchedule != null ? weeklySchedule : new ArrayList<>();
    }
    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }

    public Boolean getIsHourly() { return isHourly != null ? isHourly : false; }
    public void setIsHourly(Boolean isHourly) { this.isHourly = (isHourly != null ? isHourly : false); }

    public Boolean getIsPercentage() { return isPercentage != null ? isPercentage : false; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = (isPercentage != null ? isPercentage : false); }

    public Integer getWorkPercentage() { return workPercentage != null && workPercentage >= 0 && workPercentage <=100 ? workPercentage : 100; }
    public void setWorkPercentage(Integer workPercentage) {
        this.workPercentage = (workPercentage != null && workPercentage >= 0 && workPercentage <=100 ? workPercentage : 100);
    }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }
}