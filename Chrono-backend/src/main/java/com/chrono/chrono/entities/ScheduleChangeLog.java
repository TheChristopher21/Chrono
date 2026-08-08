package com.chrono.chrono.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_change_logs", indexes = {
        @Index(name = "idx_schedule_change_company_date_created", columnList = "company_id, schedule_date, created_at"),
        @Index(name = "idx_schedule_change_target_date", columnList = "target_user_id, schedule_date")
})
public class ScheduleChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Column(name = "schedule_entry_id")
    private Long scheduleEntryId;

    @Column(name = "actor_username", nullable = false, length = 160)
    private String actorUsername;

    @Column(name = "actor_display_name")
    private String actorDisplayName;

    @Column(name = "target_username", length = 160)
    private String targetUsername;

    @Column(name = "target_display_name")
    private String targetDisplayName;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(length = 120)
    private String shift;

    @Column(length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(User targetUser) {
        this.targetUser = targetUser;
    }

    public Long getScheduleEntryId() {
        return scheduleEntryId;
    }

    public void setScheduleEntryId(Long scheduleEntryId) {
        this.scheduleEntryId = scheduleEntryId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public void setTargetDisplayName(String targetDisplayName) {
        this.targetDisplayName = targetDisplayName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
