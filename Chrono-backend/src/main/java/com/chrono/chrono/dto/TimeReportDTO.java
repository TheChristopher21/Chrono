package com.chrono.chrono.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TimeReportDTO {
    private String username;   // Benutzername
    private String date;       // z. B. "Dienstag, 11.2.2025"
    private String workStart;
    private String breakStart;
    private String breakEnd;
    private String workEnd;
    private String dailyNote;  // Tägliche Notiz

    public TimeReportDTO(String username, String date, String workStart, String breakStart, String breakEnd, String workEnd, String dailyNote) {
        this.username = username;
        this.date = date;
        this.workStart = workStart;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.workEnd = workEnd;
        this.dailyNote = dailyNote;
    }

}
