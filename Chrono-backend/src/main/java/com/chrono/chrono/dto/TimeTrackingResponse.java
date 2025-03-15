package com.chrono.chrono.dto;

import java.time.LocalDateTime;

/**
 * Einfache DTO-Klasse für die Rückgabe eines TimeTracking-Eintrags,
 * inkl. "color" (also Art des Punches), "punchOrder" und "dailyNote".
 */
public class TimeTrackingResponse {

    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private String color;       // z. B. "Work Start", "Break Start", ...
    private Integer punchOrder; // 1..4
    private String dailyNote;   // Tägliche Notiz

    public TimeTrackingResponse(Long id,
                                LocalDateTime startTime,
                                LocalDateTime endTime,
                                boolean corrected,
                                String color,
                                Integer punchOrder,
                                String dailyNote) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.color = color;
        this.punchOrder = punchOrder;
        this.dailyNote = dailyNote;
    }

    public Long getId() {
        return id;
    }
    public LocalDateTime getStartTime() {
        return startTime;
    }
    public LocalDateTime getEndTime() {
        return endTime;
    }
    public boolean isCorrected() {
        return corrected;
    }
    public String getColor() {
        return color;
    }
    public Integer getPunchOrder() {
        return punchOrder;
    }
    public String getDailyNote() {
        return dailyNote;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    public void setCorrected(boolean corrected) {
        this.corrected = corrected;
    }
    public void setColor(String color) {
        this.color = color;
    }
    public void setPunchOrder(Integer punchOrder) {
        this.punchOrder = punchOrder;
    }
    public void setDailyNote(String dailyNote) {
        this.dailyNote = dailyNote;
    }
}
