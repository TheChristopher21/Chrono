package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTracking;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class AdminTimeTrackDTO {
    // Getter und Setter
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

}
