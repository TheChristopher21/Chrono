package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ScheduleChangeLog;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ScheduleChangeLogDTO {
    private Long id;
    private Long actorUserId;
    private String actorUsername;
    private String actorName;
    private Long targetUserId;
    private String targetUsername;
    private String targetName;
    private Long scheduleEntryId;
    private String action;
    private LocalDate scheduleDate;
    private String shift;
    private String details;
    private LocalDateTime createdAt;

    public ScheduleChangeLogDTO() {
    }

    public ScheduleChangeLogDTO(ScheduleChangeLog log) {
        this.id = log.getId();
        this.actorUserId = log.getActor() != null ? log.getActor().getId() : null;
        this.actorUsername = log.getActorUsername();
        this.actorName = log.getActorDisplayName();
        this.targetUserId = log.getTargetUser() != null ? log.getTargetUser().getId() : null;
        this.targetUsername = log.getTargetUsername();
        this.targetName = log.getTargetDisplayName();
        this.scheduleEntryId = log.getScheduleEntryId();
        this.action = log.getAction();
        this.scheduleDate = log.getScheduleDate();
        this.shift = log.getShift();
        this.details = log.getDetails();
        this.createdAt = log.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorName() {
        return actorName;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public String getTargetName() {
        return targetName;
    }

    public Long getScheduleEntryId() {
        return scheduleEntryId;
    }

    public String getAction() {
        return action;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public String getShift() {
        return shift;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
