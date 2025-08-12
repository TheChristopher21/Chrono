package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonBackReference;
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
    private boolean usesOvertime;
    @Column(name = "company_vacation")
    private boolean companyVacation;

    @Column(name = "overtime_deduction_minutes") // NEUES FELD
    private Integer overtimeDeductionMinutes; // Nur für usesOvertime = true & prozentuale User

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference("user-vacationRequests")
    private User user;

    public VacationRequest() {}

    // GETTER und SETTER
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isDenied() { return denied; }
    public void setDenied(boolean denied) { this.denied = denied; }

    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }

    public boolean isUsesOvertime() { return usesOvertime; }
    public void setUsesOvertime(boolean usesOvertime) { this.usesOvertime = usesOvertime; }

    public boolean isCompanyVacation() { return companyVacation; }
    public void setCompanyVacation(boolean companyVacation) { this.companyVacation = companyVacation; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getOvertimeDeductionMinutes() { // NEUER GETTER
        return overtimeDeductionMinutes;
    }
    public void setOvertimeDeductionMinutes(Integer overtimeDeductionMinutes) { // NEUER SETTER
        this.overtimeDeductionMinutes = overtimeDeductionMinutes;
    }

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
