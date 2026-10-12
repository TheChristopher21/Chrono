package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "correction_requests")
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_entry_id", nullable = true)
    @JsonIgnore
    private TimeTrackingEntry targetEntry;

    private LocalDateTime originalTimestamp;
    @Enumerated(EnumType.STRING)
    private TimeTrackingEntry.PunchType originalPunchType;
    private Long originalEntryId;
    @Column(nullable = false)
    private boolean originalDayCaptured;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "correction_original_entries", joinColumns = @JoinColumn(name = "correction_request_id"))
    @OrderBy("entryTimestamp ASC")
    private List<CorrectionOriginalEntry> originalDayEntries = new ArrayList<>();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = true)
    private LocalDateTime desiredTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeTrackingEntry.PunchType desiredPunchType;

    @Column(length = 1000)
    private String reason;
    private boolean approved;
    private boolean denied;

    @Column(name = "admin_comment", length = 1000)
    private String adminComment;

    @Column(name = "processed_by_admin_username", length = 255)
    private String processedByAdminUsername;

    @Column(name = "processed_by_admin_initials", length = 10)
    private String processedByAdminInitials;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference("user-correctionRequests")
    private User user;
    
    @Column(nullable = true)
    private LocalDate requestDate;

    @Transient
    private String userPassword;

    public CorrectionRequest() {}

    public CorrectionRequest(User user, LocalDate requestDate, TimeTrackingEntry targetEntry, LocalDateTime desiredTimestamp, String reason) {
        this.user = user;
        this.requestDate = requestDate;
        setTargetEntry(targetEntry);
        this.desiredTimestamp = desiredTimestamp;
        this.reason = reason;
        this.approved = false;
        this.denied = false;
        if (targetEntry != null) {
            this.desiredPunchType = targetEntry.getPunchType();
        }
    }
    
    public CorrectionRequest(User user, LocalDate requestDate, LocalDateTime desiredTimestamp, TimeTrackingEntry.PunchType desiredPunchType, String reason) {
        this.user = user;
        this.requestDate = requestDate;
        this.desiredTimestamp = desiredTimestamp;
        this.desiredPunchType = desiredPunchType;
        this.reason = reason;
        this.approved = false;
        this.denied = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TimeTrackingEntry getTargetEntry() { return targetEntry; }
    public void setTargetEntry(TimeTrackingEntry targetEntry) {
        if (targetEntry != null && originalTimestamp == null) {
            originalTimestamp = targetEntry.getEntryTimestamp();
            originalPunchType = targetEntry.getPunchType();
            originalEntryId = targetEntry.getId();
        }
        this.targetEntry = targetEntry;
    }
    @JsonIgnore
    public boolean isOriginalDayCaptured() { return originalDayCaptured; }
    public List<CorrectionOriginalEntry> getOriginalDayEntries() { return originalDayEntries; }
    public void captureOriginalDay(List<TimeTrackingEntry> entries) {
        if (originalDayCaptured) return;
        originalDayEntries = new ArrayList<>(entries.stream().map(CorrectionOriginalEntry::new).toList());
        originalDayCaptured = true;
    }
    public LocalDateTime getDesiredTimestamp() { return desiredTimestamp; }
    public void setDesiredTimestamp(LocalDateTime desiredTimestamp) { this.desiredTimestamp = desiredTimestamp; }
    public TimeTrackingEntry.PunchType getDesiredPunchType() { return desiredPunchType; }
    public void setDesiredPunchType(TimeTrackingEntry.PunchType desiredPunchType) { this.desiredPunchType = desiredPunchType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public boolean isDenied() { return denied; }
    public void setDenied(boolean denied) { this.denied = denied; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getAdminComment() { return adminComment; }
    public void setAdminComment(String adminComment) { this.adminComment = adminComment; }
    public String getProcessedByAdminUsername() { return processedByAdminUsername; }
    public void setProcessedByAdminUsername(String processedByAdminUsername) { this.processedByAdminUsername = processedByAdminUsername; }
    public String getProcessedByAdminInitials() { return processedByAdminInitials; }
    public void setProcessedByAdminInitials(String processedByAdminInitials) { this.processedByAdminInitials = processedByAdminInitials; }
    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }
    public String getUserPassword() { return userPassword; }
    public void setUserPassword(String userPassword) { this.userPassword = userPassword; }

    @JsonProperty("username")
    public String getUsername() { return (user != null) ? user.getUsername() : "Unknown"; }

    @JsonProperty("originalTimestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public LocalDateTime getOriginalTimestamp() { return originalTimestamp != null ? originalTimestamp : (targetEntry != null) ? targetEntry.getEntryTimestamp() : null; }

    @JsonProperty("originalPunchType")
    public TimeTrackingEntry.PunchType getOriginalPunchType() { return originalPunchType != null ? originalPunchType : (targetEntry != null) ? targetEntry.getPunchType() : null; }
    
    @JsonProperty("targetEntryId")
    public Long getTargetEntryId() { return originalEntryId != null ? originalEntryId : (targetEntry != null) ? targetEntry.getId() : null; }
}
