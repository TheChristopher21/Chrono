package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTrackingEntry;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight projection for correction requests used by the admin dashboard.
 * Avoids exposing the full JPA entity graph (which may contain lazy relations)
 * and keeps JSON serialization predictable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminCorrectionRequestDTO {

    private Long id;
    private String username;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate requestDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime desiredTimestamp;

    private TimeTrackingEntry.PunchType desiredPunchType;
    private String reason;
    private boolean approved;
    private boolean denied;
    private String adminComment;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime originalTimestamp;
    private TimeTrackingEntry.PunchType originalPunchType;
    private Long targetEntryId;

    public AdminCorrectionRequestDTO() {
    }

    public AdminCorrectionRequestDTO(Long id,
                                     String username,
                                     LocalDate requestDate,
                                     LocalDateTime desiredTimestamp,
                                     TimeTrackingEntry.PunchType desiredPunchType,
                                     String reason,
                                     boolean approved,
                                     boolean denied,
                                     String adminComment,
                                     LocalDateTime originalTimestamp,
                                     TimeTrackingEntry.PunchType originalPunchType,
                                     Long targetEntryId) {
        this.id = id;
        this.username = username;
        this.requestDate = requestDate;
        this.desiredTimestamp = desiredTimestamp;
        this.desiredPunchType = desiredPunchType;
        this.reason = reason;
        this.approved = approved;
        this.denied = denied;
        this.adminComment = adminComment;
        this.originalTimestamp = originalTimestamp;
        this.originalPunchType = originalPunchType;
        this.targetEntryId = targetEntryId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public LocalDateTime getDesiredTimestamp() {
        return desiredTimestamp;
    }

    public void setDesiredTimestamp(LocalDateTime desiredTimestamp) {
        this.desiredTimestamp = desiredTimestamp;
    }

    public TimeTrackingEntry.PunchType getDesiredPunchType() {
        return desiredPunchType;
    }

    public void setDesiredPunchType(TimeTrackingEntry.PunchType desiredPunchType) {
        this.desiredPunchType = desiredPunchType;
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

    public String getAdminComment() {
        return adminComment;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }

    public LocalDateTime getOriginalTimestamp() {
        return originalTimestamp;
    }

    public void setOriginalTimestamp(LocalDateTime originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }

    public TimeTrackingEntry.PunchType getOriginalPunchType() {
        return originalPunchType;
    }

    public void setOriginalPunchType(TimeTrackingEntry.PunchType originalPunchType) {
        this.originalPunchType = originalPunchType;
    }

    public Long getTargetEntryId() {
        return targetEntryId;
    }

    public void setTargetEntryId(Long targetEntryId) {
        this.targetEntryId = targetEntryId;
    }
}
