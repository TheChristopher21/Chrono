package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "schedule_entries",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "date"})
        }
)
public class ScheduleEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate date;

    // --- NEU: Feld für die Schicht ---
    private String shift; // z.B. "EARLY", "LATE", "NIGHT"

    @Column(length = 1000)
    private String note;

    public ScheduleEntry() {}

    // Konstruktor wurde zur Klarheit entfernt, da er nicht mehr alle Felder abdeckt.

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}