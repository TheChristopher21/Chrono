package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class TimeTrackingResponse {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private String color;     // "Work Start", "Break Start", ...
    private Integer punchOrder;

    public TimeTrackingResponse(
            Long id,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean corrected,
            String color,
            Integer punchOrder
    ) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.color = color;
        this.punchOrder = punchOrder;
    }

    // Getter + Setter
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
}
