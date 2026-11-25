package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTrackingEntry;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

/** Immutable values from the day before a correction; no live entry foreign key. */
@Embeddable
public class CorrectionOriginalEntry {
    @Column(name = "entry_id")
    private Long id;
    @Column(name = "entry_timestamp", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime entryTimestamp;
    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false)
    private TimeTrackingEntry.PunchType punchType;

    protected CorrectionOriginalEntry() {}

    public CorrectionOriginalEntry(TimeTrackingEntry entry) {
        id = entry.getId();
        entryTimestamp = entry.getEntryTimestamp();
        punchType = entry.getPunchType();
    }

    public Long getId() { return id; }
    public LocalDateTime getEntryTimestamp() { return entryTimestamp; }
    public TimeTrackingEntry.PunchType getPunchType() { return punchType; }
}
