package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "correction_requests")
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gewünschte (korrigierte) Start- und Endzeit (Datum + Uhrzeit)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("desiredStart")
    private LocalDateTime desiredStartTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("desiredEnd")
    private LocalDateTime desiredEndTime;

    private String reason;
    private boolean approved;
    private boolean denied;

    // Neue (korrigierte) Zeiten (nur Uhrzeit)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "work_start")
    private LocalTime workStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "break_start")
    private LocalTime breakStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "break_end")
    private LocalTime breakEnd;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "work_end")
    private LocalTime workEnd;

    // Originale Zeiten (werden beim Approve übernommen)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "original_work_start")
    private LocalTime originalWorkStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "original_break_start")
    private LocalTime originalBreakStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "original_break_end")
    private LocalTime originalBreakEnd;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    @Column(name = "original_work_end")
    private LocalTime originalWorkEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // Optional: Verknüpfung zu einem existierenden TimeTracking-Eintrag
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_tracking_id", nullable = true)
    @JsonIgnore
    private TimeTracking originalTimeTracking;

    // Transient: Benutzerpasswort (nicht persistiert)
    @Transient
    private String userPassword;

    public CorrectionRequest() {}

    public CorrectionRequest(User user, TimeTracking originalTimeTracking,
                             LocalDateTime desiredStart, LocalDateTime desiredEnd, String reason) {
        this.user = user;
        this.originalTimeTracking = originalTimeTracking;
        this.desiredStartTime = desiredStart;
        this.desiredEndTime = desiredEnd;
        this.reason = reason;
        this.approved = false;
        this.denied = false;
        if (originalTimeTracking != null) {
            this.originalWorkStart = originalTimeTracking.getWorkStart();
            this.originalBreakStart = originalTimeTracking.getBreakStart();
            this.originalBreakEnd = originalTimeTracking.getBreakEnd();
            this.originalWorkEnd = originalTimeTracking.getWorkEnd();
        }
    }

    // --- Getter & Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getDesiredStartTime() { return desiredStartTime; }
    public void setDesiredStartTime(LocalDateTime desiredStartTime) { this.desiredStartTime = desiredStartTime; }

    public LocalDateTime getDesiredEndTime() { return desiredEndTime; }
    public void setDesiredEndTime(LocalDateTime desiredEndTime) { this.desiredEndTime = desiredEndTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isDenied() { return denied; }
    public void setDenied(boolean denied) { this.denied = denied; }

    public LocalTime getWorkStart() { return workStart; }
    public void setWorkStart(LocalTime workStart) { this.workStart = workStart; }

    public LocalTime getBreakStart() { return breakStart; }
    public void setBreakStart(LocalTime breakStart) { this.breakStart = breakStart; }

    public LocalTime getBreakEnd() { return breakEnd; }
    public void setBreakEnd(LocalTime breakEnd) { this.breakEnd = breakEnd; }

    public LocalTime getWorkEnd() { return workEnd; }
    public void setWorkEnd(LocalTime workEnd) { this.workEnd = workEnd; }

    public LocalTime getOriginalWorkStart() { return originalWorkStart; }
    public void setOriginalWorkStart(LocalTime originalWorkStart) { this.originalWorkStart = originalWorkStart; }

    public LocalTime getOriginalBreakStart() { return originalBreakStart; }
    public void setOriginalBreakStart(LocalTime originalBreakStart) { this.originalBreakStart = originalBreakStart; }

    public LocalTime getOriginalBreakEnd() { return originalBreakEnd; }
    public void setOriginalBreakEnd(LocalTime originalBreakEnd) { this.originalBreakEnd = originalBreakEnd; }

    public LocalTime getOriginalWorkEnd() { return originalWorkEnd; }
    public void setOriginalWorkEnd(LocalTime originalWorkEnd) { this.originalWorkEnd = originalWorkEnd; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public TimeTracking getOriginalTimeTracking() { return originalTimeTracking; }
    public void setOriginalTimeTracking(TimeTracking originalTimeTracking) { this.originalTimeTracking = originalTimeTracking; }

    @JsonProperty("username")
    public String getUsername() {
        return (user != null) ? user.getUsername() : "Unknown";
    }

    // Hilfsmethoden zur Formatierung (HH:mm)
    public String getWorkStartFormatted() {
        return workStart != null ? workStart.format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";
    }

    public String getBreakStartFormatted() {
        return breakStart != null ? breakStart.format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";
    }

    public String getBreakEndFormatted() {
        return breakEnd != null ? breakEnd.format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";
    }

    public String getWorkEndFormatted() {
        return workEnd != null ? workEnd.format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";
    }

    // Getter & Setter für userPassword
    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    // Optional: Falls du das Datum des Korrektur-Tages separat brauchst
    public LocalDate getDate() {
        return desiredStartTime != null ? desiredStartTime.toLocalDate() : null;
    }
}
