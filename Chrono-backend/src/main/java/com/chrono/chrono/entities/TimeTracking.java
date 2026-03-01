package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "time_tracking",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "daily_date"})
        })
public class TimeTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Jede Zeile gehört zu genau einem Nutzer
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Genau ein Eintrag pro Tag
    @Column(name = "daily_date", nullable = false)
    private LocalDate dailyDate;

    // Arbeitsbeginn
    private LocalTime workStart;

    // Pausenbeginn
    private LocalTime breakStart;

    // Pausenende
    private LocalTime breakEnd;

    // Arbeitsende
    private LocalTime workEnd;

    // Falls es eine manuelle Korrektur war
    private boolean corrected;

    // Optionale Tagesnotiz für stundenbasierte Mitarbeiter
    @Column(length = 2000)
    private String dailyNote;

    // GETTER & SETTER

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getDailyDate() {
        return dailyDate;
    }
    public void setDailyDate(LocalDate dailyDate) {
        this.dailyDate = dailyDate;
    }

    public LocalTime getWorkStart() {
        return workStart;
    }
    public void setWorkStart(LocalTime workStart) {
        this.workStart = workStart;
    }

    public LocalTime getBreakStart() {
        return breakStart;
    }
    public void setBreakStart(LocalTime breakStart) {
        this.breakStart = breakStart;
    }

    public LocalTime getBreakEnd() {
        return breakEnd;
    }
    public void setBreakEnd(LocalTime breakEnd) {
        this.breakEnd = breakEnd;
    }

    public LocalTime getWorkEnd() {
        return workEnd;
    }
    public void setWorkEnd(LocalTime workEnd) {
        this.workEnd = workEnd;
    }

    public boolean isCorrected() {
        return corrected;
    }
    public void setCorrected(boolean corrected) {
        this.corrected = corrected;
    }

    public String getDailyNote() {
        return dailyNote;
    }
    public void setDailyNote(String dailyNote) {
        this.dailyNote = dailyNote;
    }
}
