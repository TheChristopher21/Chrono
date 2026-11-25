package com.chrono.chrono.dto;

import java.time.LocalDate;

public class WorkdaySwapRequestDTO {
    private String username;
    private LocalDate originalWorkDate;
    private LocalDate replacementWorkDate;
    private String note;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDate getOriginalWorkDate() { return originalWorkDate; }
    public void setOriginalWorkDate(LocalDate originalWorkDate) { this.originalWorkDate = originalWorkDate; }
    public LocalDate getReplacementWorkDate() { return replacementWorkDate; }
    public void setReplacementWorkDate(LocalDate replacementWorkDate) { this.replacementWorkDate = replacementWorkDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
