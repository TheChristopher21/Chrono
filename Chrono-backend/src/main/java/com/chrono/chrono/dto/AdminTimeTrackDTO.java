package com.chrono.chrono.dto;

import com.chrono.chrono.entities.TimeTracking;
import java.time.LocalDate;
import java.time.LocalTime;

public class AdminTimeTrackDTO {
    private Long id;
    private String username;
    private LocalDate dailyDate;
    private LocalTime workStart;
    private LocalTime breakStart;
    private LocalTime breakEnd;
    private LocalTime workEnd;
    private boolean corrected;
    private Long companyId;

    public AdminTimeTrackDTO(TimeTracking tt) {
        this.id = tt.getId();
        this.username = tt.getUser().getUsername();
        this.dailyDate = tt.getDailyDate();
        this.workStart = tt.getWorkStart();
        this.breakStart = tt.getBreakStart();
        this.breakEnd = tt.getBreakEnd();
        this.workEnd = tt.getWorkEnd();
        this.corrected = tt.isCorrected();
        this.companyId = tt.getUser().getCompany().getId();
    }

    // GETTER / SETTER
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public LocalDate getDailyDate() { return dailyDate; }
    public LocalTime getWorkStart() { return workStart; }
    public LocalTime getBreakStart() { return breakStart; }
    public LocalTime getBreakEnd() { return breakEnd; }
    public LocalTime getWorkEnd() { return workEnd; }
    public boolean isCorrected() { return corrected; }
    public Long getCompanyId() { return companyId; }
}
