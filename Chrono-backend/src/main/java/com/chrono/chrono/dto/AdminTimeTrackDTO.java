// src/main/java/com/chrono/chrono/dto/AdminTimeTrackDTO.java
package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class AdminTimeTrackDTO {
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private Integer punchOrder;

    public AdminTimeTrackDTO(String username, LocalDateTime startTime, LocalDateTime endTime, boolean corrected, Integer punchOrder) {
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.punchOrder = punchOrder;
    }

    public String getUsername() { return username; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isCorrected() { return corrected; }
    public Integer getPunchOrder() { return punchOrder; }
}
