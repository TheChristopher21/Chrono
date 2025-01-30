package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class TimeTrackingRequest {

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public TimeTrackingRequest() {
    }

    public TimeTrackingRequest(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getter/Setter
    public LocalDateTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    public LocalDateTime getEndTime() {
        return endTime;
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
