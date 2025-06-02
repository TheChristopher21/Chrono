package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DailyTimeSummaryDTO {
    private LocalDate date;
    private int workedMinutes;
    private int breakMinutes;
    private List<TimeTrackingEntryDTO> entries;
    private String dailyNote;
    private boolean needsCorrection;
    private PrimaryTimes primaryTimes;

    public static class PrimaryTimes {
        private LocalTime firstStartTime;
        private LocalTime lastEndTime;
        private boolean isOpen;

        public PrimaryTimes(LocalTime firstStartTime, LocalTime lastEndTime, boolean isOpen) {
            this.firstStartTime = firstStartTime;
            this.lastEndTime = lastEndTime;
            this.isOpen = isOpen;
        }
        public LocalTime getFirstStartTime() { return firstStartTime; }
        public LocalTime getLastEndTime() { return lastEndTime; }
        public boolean isOpen() { return isOpen; }
    }

    public DailyTimeSummaryDTO(LocalDate date, int workedMinutes, int breakMinutes, List<TimeTrackingEntryDTO> entries, String dailyNote, boolean needsCorrection, PrimaryTimes primaryTimes) {
        this.date = date;
        this.workedMinutes = workedMinutes;
        this.breakMinutes = breakMinutes;
        this.entries = entries;
        this.dailyNote = dailyNote;
        this.needsCorrection = needsCorrection;
        this.primaryTimes = primaryTimes;
    }

    public LocalDate getDate() { return date; }
    public int getWorkedMinutes() { return workedMinutes; }
    public int getBreakMinutes() { return breakMinutes; }
    public List<TimeTrackingEntryDTO> getEntries() { return entries; }
    public String getDailyNote() { return dailyNote; }
    public boolean isNeedsCorrection() { return needsCorrection; }
    public PrimaryTimes getPrimaryTimes() { return primaryTimes; }
}
