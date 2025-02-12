package com.chrono.chrono.dto;

public class TimeReportDTO {
    private String username;   // Neuer Benutzername
    private String date;       // z. B. "Dienstag, 11.2.2025"
    private String workStart;
    private String breakStart;
    private String breakEnd;
    private String workEnd;

    public TimeReportDTO(String username, String date, String workStart, String breakStart, String breakEnd, String workEnd) {
        this.username = username;
        this.date = date;
        this.workStart = workStart;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.workEnd = workEnd;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }

    public String getWorkStart() {
        return workStart;
    }
    public void setWorkStart(String workStart) {
        this.workStart = workStart;
    }

    public String getBreakStart() {
        return breakStart;
    }
    public void setBreakStart(String breakStart) {
        this.breakStart = breakStart;
    }

    public String getBreakEnd() {
        return breakEnd;
    }
    public void setBreakEnd(String breakEnd) {
        this.breakEnd = breakEnd;
    }

    public String getWorkEnd() {
        return workEnd;
    }
    public void setWorkEnd(String workEnd) {
        this.workEnd = workEnd;
    }
}
