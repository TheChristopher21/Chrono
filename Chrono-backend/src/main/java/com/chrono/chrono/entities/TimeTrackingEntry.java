package com.chrono.chrono.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.time.LocalDateTime;
import java.time.LocalDate; // Import für getEntryDate
import java.time.LocalTime; // Import für getEntryTime

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;

@Entity
@Table(name = "time_tracking_entries",
        indexes = {
                // Index für schnelle Abfragen pro User und Zeit
                @Index(columnList = "user_id, entry_timestamp")
        })
public class TimeTrackingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference("user-timeTrackingEntries")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp; // Präziser Zeitstempel für das Ereignis

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false)
    private PunchType punchType; // START oder ENDE

    @Enumerated(EnumType.STRING)
    @Column(name = "source") // Quelle des Stempels, z.B. NFC, Manuell, System
    private PunchSource source;

    @Column(name = "corrected_by_user", nullable = false)
    private boolean correctedByUser = false;

    @Column(name = "system_generated_note", length = 255)
    private String systemGeneratedNote; // z.B. "Automatischer Arbeitsende-Stempel"

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "approved", nullable = false)
    private boolean approved = false;

    public enum PunchType {
        START,
        ENDE
    }

    public enum PunchSource {
        NFC_SCAN,               // Stempelung via NFC-Leser
        MANUAL_PUNCH,           // Stempelung über Frontend-Button durch den User selbst
        SYSTEM_AUTO_END,        // Automatischer 23:20 Stempel durch den nächtlichen Job
        ADMIN_CORRECTION,       // Ein Admin hat diesen Eintrag manuell erstellt oder korrigiert
        USER_CORRECTION,        // Der User hat diesen Eintrag über einen Korrekturantrag erstellt/geändert
        MANUAL_IMPORT,          // Allgemeiner Typ für importierte Daten (z.B. aus Excel)

        // Spezifische Konstanten für die Altdatenmigration aus dem V1-System
        MIGRATION_V1_WORK_START,
        MIGRATION_V1_BREAK_START,
        MIGRATION_V1_BREAK_END,
        MIGRATION_V1_WORK_END
    }

    public TimeTrackingEntry() {
    }

    public TimeTrackingEntry(User user, LocalDateTime entryTimestamp, PunchType punchType, PunchSource source) {
        this.user = user;
        this.entryTimestamp = entryTimestamp;
        this.punchType = punchType;
        this.source = source;
    }

    public TimeTrackingEntry(User user, Customer customer, LocalDateTime entryTimestamp, PunchType punchType, PunchSource source) {
        this.user = user;
        this.customer = customer;
        this.entryTimestamp = entryTimestamp;
        this.punchType = punchType;
        this.source = source;
    }

    public TimeTrackingEntry(User user, Customer customer, Project project, LocalDateTime entryTimestamp, PunchType punchType, PunchSource source) {
        this.user = user;
        this.customer = customer;
        this.project = project;
        this.entryTimestamp = entryTimestamp;
        this.punchType = punchType;
        this.source = source;
    }

    // Getter und Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
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
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    @Transient
    public LocalDate getEntryDate() {
        return entryTimestamp != null ? entryTimestamp.toLocalDate() : null;
    }

    @Transient
    public LocalTime getEntryTime() {
        return entryTimestamp != null ? entryTimestamp.toLocalTime() : null;
    }
}