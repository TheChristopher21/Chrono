package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "time_tracking")
public class TimeTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;

    private Integer punchOrder;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Felder für korrigierte Zeiten (nur Uhrzeit)
    @Column(name = "work_start", columnDefinition = "TIME")
    private LocalTime workStart;

    @Column(name = "break_start", columnDefinition = "TIME")
    private LocalTime breakStart;

    @Column(name = "break_end", columnDefinition = "TIME")
    private LocalTime breakEnd;

    @Column(name = "work_end", columnDefinition = "TIME")
    private LocalTime workEnd;

    // Tägliche Notiz (dailyNote) für Stundenlöhner
    @Column(name = "daily_note", columnDefinition = "TEXT")
    private String dailyNote;

    // Reines Datum für die Notiz (ohne Uhrzeit – vermeidet Zeitzonenprobleme)
    @Column(name = "daily_date")
    private LocalDate dailyDate;

    public TimeTracking() {
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public boolean isCorrected() { return corrected; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }

    public Integer getPunchOrder() { return punchOrder; }
    public void setPunchOrder(Integer punchOrder) { this.punchOrder = punchOrder; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalTime getWorkStart() { return workStart; }
    public void setWorkStart(LocalTime workStart) { this.workStart = workStart; }

    public LocalTime getBreakStart() { return breakStart; }
    public void setBreakStart(LocalTime breakStart) { this.breakStart = breakStart; }

    public LocalTime getBreakEnd() { return breakEnd; }
    public void setBreakEnd(LocalTime breakEnd) { this.breakEnd = breakEnd; }

    public LocalTime getWorkEnd() { return workEnd; }
    public void setWorkEnd(LocalTime workEnd) { this.workEnd = workEnd; }

    public String getDailyNote() { return dailyNote; }
    public void setDailyNote(String dailyNote) { this.dailyNote = dailyNote; }

    public LocalDate getDailyDate() { return dailyDate; }
    public void setDailyDate(LocalDate dailyDate) { this.dailyDate = dailyDate; }
}
