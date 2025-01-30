package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "time_tracking")
public class TimeTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public TimeTracking() {}

    public TimeTracking(LocalDateTime startTime, LocalDateTime endTime, User user) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.user = user;
    }

    // Getter / Setter
    public Long getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isCorrected() { return corrected; }
    public User getUser() { return user; }

    public void setId(Long id) { this.id = id; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }
    public void setUser(User user) { this.user = user; }
}
