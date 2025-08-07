package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTrackingEntry;
import java.time.LocalDateTime;

public class TimeTrackingEntryDTO {
    private Long id;
    private String username;
    private Long customerId;
    private String customerName;
    private Long projectId;
    private String projectName;
    private Long taskId;
    private String taskName;
    private Integer durationMinutes;
    private String description;
    private boolean approved;
    private LocalDateTime entryTimestamp;
    private TimeTrackingEntry.PunchType punchType;
    private TimeTrackingEntry.PunchSource source;
    private boolean correctedByUser;
    private String systemGeneratedNote;

    public TimeTrackingEntryDTO(Long id, String username, Long customerId, String customerName, Long projectId, String projectName, Long taskId, String taskName, Integer durationMinutes, String description, boolean approved, LocalDateTime entryTimestamp, TimeTrackingEntry.PunchType punchType, TimeTrackingEntry.PunchSource source, boolean correctedByUser, String systemGeneratedNote) {
        this.id = id;
        this.username = username;
        this.customerId = customerId;
        this.customerName = customerName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.taskId = taskId;
        this.taskName = taskName;
        this.durationMinutes = durationMinutes;
        this.description = description;
        this.approved = approved;
        this.entryTimestamp = entryTimestamp;
        this.punchType = punchType;
        this.source = source;
        this.correctedByUser = correctedByUser;
        this.systemGeneratedNote = systemGeneratedNote;
    }
    
    public static TimeTrackingEntryDTO fromEntity(TimeTrackingEntry entry) {
        if (entry == null) return null;
        return new TimeTrackingEntryDTO(
            entry.getId(),
            entry.getUser() != null ? entry.getUser().getUsername() : null,
            entry.getCustomer() != null ? entry.getCustomer().getId() : null,
            entry.getCustomer() != null ? entry.getCustomer().getName() : null,
            entry.getProject() != null ? entry.getProject().getId() : null,
            entry.getProject() != null ? entry.getProject().getName() : null,
            entry.getTask() != null ? entry.getTask().getId() : null,
            entry.getTask() != null ? entry.getTask().getName() : null,
            entry.getDurationMinutes(),
            entry.getDescription(),
            entry.isApproved(),
            entry.getEntryTimestamp(),
            entry.getPunchType(),
            entry.getSource(),
            entry.isCorrectedByUser(),
            entry.getSystemGeneratedNote()
        );
    }


    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Long getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public Long getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getDescription() { return description; }
    public boolean isApproved() { return approved; }
    public LocalDateTime getEntryTimestamp() { return entryTimestamp; }
    public TimeTrackingEntry.PunchType getPunchType() { return punchType; }
    public TimeTrackingEntry.PunchSource getSource() { return source; }
    public boolean isCorrectedByUser() { return correctedByUser; }
    public String getSystemGeneratedNote() { return systemGeneratedNote; }
}
