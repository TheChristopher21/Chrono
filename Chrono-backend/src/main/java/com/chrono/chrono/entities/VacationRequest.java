package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "vacation_requests")
public class VacationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate startDate;
    private LocalDate endDate;
    private boolean approved;
    private boolean denied;
    private boolean halfDay;

    // ✅ NEU: Urlaub über Überstunden
    private boolean usesOvertime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    public VacationRequest() {}

    public Long getId() { return id; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isApproved() { return approved; }
    public boolean isDenied() { return denied; }
    public boolean isHalfDay() { return halfDay; }
    public boolean isUsesOvertime() { return usesOvertime; }
    public User getUser() { return user; }

    public void setId(Long id) { this.id = id; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public void setDenied(boolean denied) { this.denied = denied; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
    public void setUsesOvertime(boolean usesOvertime) { this.usesOvertime = usesOvertime; }
    public void setUser(User user) { this.user = user; }

    @JsonProperty("username")
    public String getUsername() {
        return (user != null) ? user.getUsername() : "Unknown";
    }

    @JsonProperty("color")
    public String getColor() {
        return (user != null && user.getColor() != null && !user.getColor().isEmpty())
                ? user.getColor() : "#ccc";
    }
}
