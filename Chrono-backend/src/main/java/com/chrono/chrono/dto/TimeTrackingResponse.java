package com.chrono.chrono.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class TimeTrackingResponse {
    // GETTER
    private final Long id;
    private final LocalDate dailyDate;
    private final LocalTime workStart;
    private final LocalTime breakStart;
    private final LocalTime breakEnd;
    private final LocalTime workEnd;
    private final String dailyNote;
    private final boolean corrected;

    // Constructor
    public TimeTrackingResponse(Long id,
                                LocalDate dailyDate,
                                LocalTime workStart,
                                LocalTime breakStart,
                                LocalTime breakEnd,
                                LocalTime workEnd,
                                String dailyNote,
                                boolean corrected) {
        this.id = id;
        this.dailyDate = dailyDate;
        this.workStart = workStart;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.workEnd = workEnd;
        this.dailyNote = dailyNote;
        this.corrected = corrected;
    }

}
