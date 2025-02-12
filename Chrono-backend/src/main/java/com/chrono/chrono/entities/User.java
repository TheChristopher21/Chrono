package com.chrono.chrono.entities;

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
    private Integer expectedWorkDays;

    @Column(name = "daily_work_hours")
    private Double dailyWorkHours;

    @Column(name = "break_duration")
    private Integer breakDuration;

    @Column(name = "color")
    private String color;

    // Neue Felder für den individuellen Wochenplan:
    @Column(name = "schedule_cycle")
    private Integer scheduleCycle;

    @Lob
    @Convert(converter = WeeklyScheduleConverter.class)
    @Column(name = "weekly_schedule")
    private List<Map<String, Integer>> weeklySchedule;

    // Feld, ab wann die aktuelle Konfiguration gilt (zukunftsorientiert)
    @Column(name = "schedule_effective_date")
    private LocalDate scheduleEffectiveDate;

    public User() {}

    // Getter & Setter

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

    public Integer getExpectedWorkDays() { return expectedWorkDays; }
    public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }

    public Double getDailyWorkHours() { return dailyWorkHours; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }

    public Integer getBreakDuration() { return breakDuration; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getScheduleCycle() { return scheduleCycle; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }

    public List<Map<String, Integer>> getWeeklySchedule() { return weeklySchedule; }
    public void setWeeklySchedule(List<Map<String, Integer>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }
}
