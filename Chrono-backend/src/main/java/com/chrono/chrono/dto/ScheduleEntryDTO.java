package com.chrono.chrono.dto;

import com.chrono.chrono.entities.ScheduleEntry;

import java.time.LocalDate;

public class ScheduleEntryDTO {
    private Long id;
    private Long userId;
    private LocalDate date;
    private String shift;

    public ScheduleEntryDTO() {}

    public ScheduleEntryDTO(ScheduleEntry entry) {
        this.id = entry.getId();
        this.userId = entry.getUser() != null ? entry.getUser().getId() : null;
        this.date = entry.getDate();
        this.shift = entry.getShift();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
}
