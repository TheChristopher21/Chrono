package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "payslip_schedules")
public class PayslipSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private Integer dayOfMonth;
    private LocalDate nextRun;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public LocalDate getNextRun() { return nextRun; }
    public void setNextRun(LocalDate nextRun) { this.nextRun = nextRun; }
}
