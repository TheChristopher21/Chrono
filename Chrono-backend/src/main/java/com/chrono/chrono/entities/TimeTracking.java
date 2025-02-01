// src/main/java/com/chrono/chrono/entities/TimeTracking.java
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

    // punchOrder: 1 = Arbeitsbeginn, 2 = Pausenbeginn, 3 = Pausenende, 4 = Arbeitsende
    private Integer punchOrder;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public TimeTracking() {}

    public Long getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isCorrected() { return corrected; }
    public Integer getPunchOrder() { return punchOrder; }
    public User getUser() { return user; }

    public void setId(Long id) { this.id = id; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }
    public void setPunchOrder(Integer punchOrder) { this.punchOrder = punchOrder; }
    public void setUser(User user) { this.user = user; }
}
