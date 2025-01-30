package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "correction_requests")
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime desiredStartTime;
    private LocalDateTime desiredEndTime;
    private String reason;

    private boolean approved;
    private boolean denied;

    // Wer hat diesen Antrag erstellt?
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Optional: Welcher TimeTracking-Eintrag soll korrigiert werden?
    @ManyToOne
    @JoinColumn(name = "time_tracking_id")
    private TimeTracking originalTimeTracking;

    public CorrectionRequest() {
    }

    // --- Getter / Setter ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDesiredStartTime() {
        return desiredStartTime;
    }

    public void setDesiredStartTime(LocalDateTime desiredStartTime) {
        this.desiredStartTime = desiredStartTime;
    }

    public LocalDateTime getDesiredEndTime() {
        return desiredEndTime;
    }

    public void setDesiredEndTime(LocalDateTime desiredEndTime) {
        this.desiredEndTime = desiredEndTime;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean isDenied() {
        return denied;
    }

    public void setDenied(boolean denied) {
        this.denied = denied;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TimeTracking getOriginalTimeTracking() {
        return originalTimeTracking;
    }

    public void setOriginalTimeTracking(TimeTracking originalTimeTracking) {
        this.originalTimeTracking = originalTimeTracking;
    }
}
