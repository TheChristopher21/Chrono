package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTrackingEntry;
import java.time.LocalDateTime;

public class TimeTrackingEntryDTO {
    private Long id;
    private String username;
    private LocalDateTime entryTimestamp;
    private TimeTrackingEntry.PunchType punchType;
    private TimeTrackingEntry.PunchSource source;
    private boolean correctedByUser;
    private String systemGeneratedNote;

    public TimeTrackingEntryDTO(Long id, String username, LocalDateTime entryTimestamp, TimeTrackingEntry.PunchType punchType, TimeTrackingEntry.PunchSource source, boolean correctedByUser, String systemGeneratedNote) {
        this.id = id;
        this.username = username;
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
            entry.getEntryTimestamp(),
            entry.getPunchType(),
            entry.getSource(),
            entry.isCorrectedByUser(),
            entry.getSystemGeneratedNote()
        );
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public LocalDateTime getEntryTimestamp() { return entryTimestamp; }
    public TimeTrackingEntry.PunchType getPunchType() { return punchType; }
    public TimeTrackingEntry.PunchSource getSource() { return source; }
    public boolean isCorrectedByUser() { return correctedByUser; }
    public String getSystemGeneratedNote() { return systemGeneratedNote; }
}
