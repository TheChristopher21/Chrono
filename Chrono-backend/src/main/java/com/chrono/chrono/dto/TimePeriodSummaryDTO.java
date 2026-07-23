package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.util.List;

public class TimePeriodSummaryDTO {
    private final String username;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int workedMinutes;
    private final int breakMinutes;
    private final int expectedMinutes;
    private final int differenceMinutes;
    private final Integer trackingBalance;
    private final List<DailyTimeSummaryDTO> dailySummaries;

    public TimePeriodSummaryDTO(
            String username,
            LocalDate startDate,
            LocalDate endDate,
            int workedMinutes,
            int breakMinutes,
            int expectedMinutes,
            int differenceMinutes,
            Integer trackingBalance,
            List<DailyTimeSummaryDTO> dailySummaries
    ) {
        this.username = username;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workedMinutes = workedMinutes;
        this.breakMinutes = breakMinutes;
        this.expectedMinutes = expectedMinutes;
        this.differenceMinutes = differenceMinutes;
        this.trackingBalance = trackingBalance;
        this.dailySummaries = dailySummaries;
    }

    public String getUsername() { return username; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getWorkedMinutes() { return workedMinutes; }
    public int getActualMinutes() { return workedMinutes; }
    public int getBreakMinutes() { return breakMinutes; }
    public int getExpectedMinutes() { return expectedMinutes; }
    public int getDifferenceMinutes() { return differenceMinutes; }
    public Integer getTrackingBalance() { return trackingBalance; }
    public List<DailyTimeSummaryDTO> getDailySummaries() { return dailySummaries; }
}
