package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ScheduleEntry;

import java.time.LocalDate;

public class ScheduleEntryDTO {
    private Long id;
    private Long userId;
    private LocalDate date;
    // --- NEU: Feld für die Schicht ---
    private String shift;
    private String note;

    public ScheduleEntryDTO() {}

    public ScheduleEntryDTO(ScheduleEntry entry) {
        this.id = entry.getId();
        this.userId = entry.getUser() != null ? entry.getUser().getId() : null;
        this.date = entry.getDate();
        this.shift = entry.getShift(); // Mappen des neuen Feldes
        this.note = entry.getNote();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}