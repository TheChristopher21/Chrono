package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class AdminTimeTrackDTO {
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private int punchOrder;
    private String color; // Neues Feld

    public AdminTimeTrackDTO(String username, LocalDateTime startTime, LocalDateTime endTime, boolean corrected, int punchOrder, String color) {
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.punchOrder = punchOrder;
        this.color = color;
    }

    // Getter & Setter
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public boolean isCorrected() { return corrected; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }
    public int getPunchOrder() { return punchOrder; }
    public void setPunchOrder(int punchOrder) { this.punchOrder = punchOrder; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
