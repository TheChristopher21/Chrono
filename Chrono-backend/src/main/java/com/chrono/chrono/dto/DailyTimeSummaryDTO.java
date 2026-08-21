package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DailyTimeSummaryDTO {
    private String username; // NEUES FELD
    private LocalDate date;
    private int workedMinutes;
    private int breakMinutes;
    private List<TimeTrackingEntryDTO> entries;
    private String dailyNote;
    private boolean needsCorrection;
    private PrimaryTimes primaryTimes;
    private Integer expectedMinutes;
    private Integer differenceMinutes;
    private Boolean hasTrackedEntries;

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

    // Konstruktor angepasst
    public DailyTimeSummaryDTO(String username, LocalDate date, int workedMinutes, int breakMinutes, List<TimeTrackingEntryDTO> entries, String dailyNote, boolean needsCorrection, PrimaryTimes primaryTimes) {
        this.username = username; // Hinzugefügt
        this.date = date;
        this.workedMinutes = workedMinutes;
        this.breakMinutes = breakMinutes;
        this.entries = entries;
        this.dailyNote = dailyNote;
        this.needsCorrection = needsCorrection;
        this.primaryTimes = primaryTimes;
        this.hasTrackedEntries = entries != null && !entries.isEmpty();
    }

    // Getter
    public String getUsername() { return username; } // Hinzugefügt
    public LocalDate getDate() { return date; }
    public int getWorkedMinutes() { return workedMinutes; }
    public int getBreakMinutes() { return breakMinutes; }
    public List<TimeTrackingEntryDTO> getEntries() { return entries; }
    public String getDailyNote() { return dailyNote; }
    public boolean isNeedsCorrection() { return needsCorrection; }
    public PrimaryTimes getPrimaryTimes() { return primaryTimes; }
    public Integer getExpectedMinutes() { return expectedMinutes; }
    public Integer getDifferenceMinutes() { return differenceMinutes; }
    public Boolean getHasTrackedEntries() { return hasTrackedEntries; }

    public void setExpectedMinutes(Integer expectedMinutes) { this.expectedMinutes = expectedMinutes; }
    public void setDifferenceMinutes(Integer differenceMinutes) { this.differenceMinutes = differenceMinutes; }
    public void setHasTrackedEntries(Boolean hasTrackedEntries) { this.hasTrackedEntries = hasTrackedEntries; }
}
