package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTracking;

import java.time.LocalDateTime;

public class AdminTimeTrackDTO {
    private Long id;
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private int punchOrder;
    private String color;
    private Long companyId;

    public AdminTimeTrackDTO(Long id, String username, LocalDateTime startTime, LocalDateTime endTime, boolean corrected, int punchOrder, String color) {
        this.id = id;
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.punchOrder = punchOrder;
        this.color = color;
    }


    //  Konstruktor aus Entity
    public AdminTimeTrackDTO(TimeTracking tt) {
        this.id         = tt.getId();
        this.username   = tt.getUser().getUsername();
        this.startTime  = tt.getStartTime();
        this.endTime    = tt.getEndTime();
        this.corrected  = tt.isCorrected();
        this.punchOrder = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
        this.color      = tt.getUser().getColor();
        this.companyId  = (tt.getUser().getCompany() != null)
                ? tt.getUser().getCompany().getId()
                : null;
    }

    // Getter und Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
