package com.chrono.chrono.dto;

import lombok.Getter;

@Getter
public class TimeReportDTO {
    // GETTER
    private final String username;
    private final String date;       // z.B. "Montag, 1.5.2025"
    private final String workStart;
    private final String breakStart;
    private final String breakEnd;
    private final String workEnd;
    private final String dailyNote;

    public TimeReportDTO(String username,
                         String date,
                         String workStart,
                         String breakStart,
                         String breakEnd,
                         String workEnd,
                         String dailyNote) {
        this.username = username;
        this.date = date;
        this.workStart = workStart;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.workEnd = workEnd;
        this.dailyNote = dailyNote;
    }

}
