package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "time_tracking_entries",
        indexes = {
                @Index(columnList = "user_id, entry_timestamp")
        })
public class TimeTrackingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false)
    private PunchType punchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private PunchSource source;

    @Column(name = "corrected_by_user", nullable = false)
    private boolean correctedByUser = false;

    @Column(name = "system_generated_note", length = 255)
    private String systemGeneratedNote;

    public enum PunchType {
        START,
        ENDE
    }

    public enum PunchSource {
        NFC_SCAN,
        MANUAL_PUNCH,
        SYSTEM_AUTO_END,
        ADMIN_CORRECTION,
        USER_CORRECTION
    }

    public TimeTrackingEntry() {
    }

    public TimeTrackingEntry(User user, LocalDateTime entryTimestamp, PunchType punchType, PunchSource source) {
        this.user = user;
        this.entryTimestamp = entryTimestamp;
        this.punchType = punchType;
        this.source = source;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getEntryTimestamp() { return entryTimestamp; }
    public void setEntryTimestamp(LocalDateTime entryTimestamp) { this.entryTimestamp = entryTimestamp; }
    public PunchType getPunchType() { return punchType; }
    public void setPunchType(PunchType punchType) { this.punchType = punchType; }
    public PunchSource getSource() { return source; }
    public void setSource(PunchSource source) { this.source = source; }
    public boolean isCorrectedByUser() { return correctedByUser; }
    public void setCorrectedByUser(boolean correctedByUser) { this.correctedByUser = correctedByUser; }
    public String getSystemGeneratedNote() { return systemGeneratedNote; }
    public void setSystemGeneratedNote(String systemGeneratedNote) { this.systemGeneratedNote = systemGeneratedNote; }

    @Transient
    public java.time.LocalDate getEntryDate() {
        return entryTimestamp != null ? entryTimestamp.toLocalDate() : null;
    }

    @Transient
    public java.time.LocalTime getEntryTime() {
        return entryTimestamp != null ? entryTimestamp.toLocalTime() : null;
    }
}
