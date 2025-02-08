package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "user_schedule_rules")  // oder wie du es nennen willst
public class UserScheduleRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Referenz auf den User, dem diese Regel gehört.
     */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Art der Regel, z.B. "EVERY_2_WEEKS_FRIDAY_OFF"
     */
    private String ruleType;

    /**
     * Startdatum der Regel, optional
     */
    private LocalDate startDate;

    /**
     * Intervall in Tagen, z.B. 14
     */
    private Integer repeatIntervalDays;

    /**
     * Wochentag (1=Montag, 2=Dienstag,... 5=Freitag usw.)
     */
    private Integer dayOfWeek;

    /**
     * dayMode: "OFF", "HALF_DAY", ...
     */
    private String dayMode;

    public UserScheduleRule() {
    }

    // Getter & Setter

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Integer getRepeatIntervalDays() { return repeatIntervalDays; }
    public void setRepeatIntervalDays(Integer repeatIntervalDays) {
        this.repeatIntervalDays = repeatIntervalDays;
    }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getDayMode() { return dayMode; }
    public void setDayMode(String dayMode) { this.dayMode = dayMode; }
}
