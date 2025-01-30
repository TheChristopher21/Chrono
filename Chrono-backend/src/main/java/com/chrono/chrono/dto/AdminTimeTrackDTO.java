package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class AdminTimeTrackDTO {

    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;

    public AdminTimeTrackDTO() {}

    public AdminTimeTrackDTO(String username, LocalDateTime startTime, LocalDateTime endTime, boolean corrected) {
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
    }

    // Getter/Setter
    public String getUsername() {
        return username;
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

    public void setUsername(String username) {
        this.username = username;
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
}
