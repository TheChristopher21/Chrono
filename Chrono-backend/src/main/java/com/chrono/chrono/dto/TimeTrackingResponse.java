package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class TimeTrackingResponse {

    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private String statusColor;

    public TimeTrackingResponse() {}

    public TimeTrackingResponse(Long id, LocalDateTime startTime, LocalDateTime endTime, boolean corrected, String statusColor) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.statusColor = statusColor;
    }

    // Getter/Setter
    public Long getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isCorrected() { return corrected; }
    public String getStatusColor() { return statusColor; }

    public void setId(Long id) { this.id = id; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public void setCorrected(boolean corrected) { this.corrected = corrected; }
    public void setStatusColor(String statusColor) { this.statusColor = statusColor; }
}
