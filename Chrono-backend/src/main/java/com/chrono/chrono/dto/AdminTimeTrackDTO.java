// src/main/java/com/chrono/chrono/dto/AdminTimeTrackDTO.java
package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class AdminTimeTrackDTO {
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private Integer punchOrder;

    // Neuer Konstruktor, der alle benötigten Parameter erhält:
    public AdminTimeTrackDTO(String username, LocalDateTime startTime, LocalDateTime endTime, boolean corrected, Integer punchOrder) {
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.punchOrder = punchOrder;
    }

    // Getter und Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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

    public boolean isCorrected() {
        return corrected;
    }

    public void setCorrected(boolean corrected) {
        this.corrected = corrected;
    }

    public Integer getPunchOrder() {
        return punchOrder;
    }

    public void setPunchOrder(Integer punchOrder) {
        this.punchOrder = punchOrder;
    }
}
