package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "correction_requests")
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("desiredStart")
    private LocalDateTime desiredStartTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("desiredEnd")
    private LocalDateTime desiredEndTime;

    private String reason;
    private boolean approved;
    private boolean denied;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore  // Verhindert Endlosschleife
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_tracking_id", nullable = true)
    @JsonIgnore  // Falls nicht nötig, entferne dies
    private TimeTracking originalTimeTracking;




    // **🚀 Neuer Fix: Leere Konstruktoren & Benutzername direkt abrufbar**
    public CorrectionRequest() {}

    public CorrectionRequest(User user, TimeTracking originalTimeTracking, LocalDateTime desiredStart, LocalDateTime desiredEnd, String reason) {
        this.user = user;
        this.originalTimeTracking = originalTimeTracking;
        this.desiredStartTime = desiredStart;
        this.desiredEndTime = desiredEnd;
        this.reason = reason;
        this.approved = false;
        this.denied = false;
    }

    // **✅ Fix für `getUser()`-Fehler**
    @JsonProperty("username")
    public String getUsername() {
        return (user != null) ? user.getUsername() : "Unknown";
    }

    @JsonProperty("originalStart")
    public LocalDateTime getOriginalStartTime() {
        if (originalTimeTracking != null) {
            return originalTimeTracking.getStartTime();
        }
        return desiredStartTime; // Falls keine Originalzeit gefunden wird, schicke nicht die gleiche Zeit zurück!
    }


    @JsonProperty("originalEnd")
    public LocalDateTime getOriginalEndTime() {
        return (originalTimeTracking != null) ? originalTimeTracking.getEndTime() : null;
    }

    // --- Getter / Setter ---

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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public TimeTracking getOriginalTimeTracking() { return originalTimeTracking; }
    public void setOriginalTimeTracking(TimeTracking originalTimeTracking) { this.originalTimeTracking = originalTimeTracking; }
}
