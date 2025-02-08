package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
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

    // Für Time-Tracking
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<TimeTracking> timeTracks = new HashSet<>();

    /**
     * Beispiel: 5 (Montag-Freitag)
     */
    @Column(name = "expected_work_days")
    private Integer expectedWorkDays;

    /**
     * Beispiel: 8.0 Stunden pro Tag
     */
    @Column(name = "daily_work_hours")
    private Double dailyWorkHours;

    /**
     * Beispiel: 30 Minuten Pause
     */
    @Column(name = "break_duration")
    private Integer breakDuration;

    public User() {}

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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
}
